# Architecture · 架构设计

本文展开讲 `ontos-rule-core` 的内部组件、数据流和关键设计取舍。
看完后你应当能：
- 知道一次 `engine.eval(...)` 在内部走了哪些类
- 知道为什么 Core 不依赖 Spring、不依赖 JPA
- 知道在哪里加你的扩展（接哪个接口）

---

## 1. 模块边界

| 模块 | 依赖什么 | 不依赖什么 | 暴露什么 |
|------|----------|------------|----------|
| **core** | `dev.cel:cel` · `slf4j-api` · `HikariCP`（SQL Backend 用）· JDK 21 | Spring · JPA · Web 框架 · 数据库 | `RuleEngine` 接口 + 一组 model + 后端/记录器 SPI |
| **business** | `core` · `spring-boot-starter-web` · `spring-boot-starter-data-jpa` · `h2` | 任何特定上游业务实体 | REST API + Domain（Rule/RuleRun/QualityScore） |
| **web** | `axios` · `vue` · `element-plus` · `echarts` | 后端实现细节 | Playground / 规则管理 / 评分 / 调用日志 UI |

**为什么 Core 不依赖 Spring？**

Core 要能被任意 Java 应用（也许是个老的 Servlet 项目，也许是个 CLI 工具，也许是另一个微服务）通过 `new DefaultRuleEngine.builder().build()` 直接用。一旦绑 Spring，就强制集成方也得是 Spring，门槛瞬间高一截。

---

## 2. Core 的五大组件

```
┌────────────────────────────────────────────────────────────────────┐
│                          RuleEngine (interface)                    │
│                                                                    │
│  compile(expr)          eval(rule, row)         execute(rule, …)   │
│  ──────────────         ─────────────────       ─────────────────  │
│        │                       │                       │           │
│        ▼                       ▼                       ▼           │
│  ┌──────────────┐    ┌─────────────────┐    ┌────────────────────┐ │
│  │ CelCompiler  │    │ BackendRouter   │    │ ExecutionBackend   │ │
│  │  - parse     │    │  - route(rule,  │    │  ┌──────────────┐  │ │
│  │  - check     │    │     rows, hint) │    │  │ JvmBackend   │  │ │
│  │  - AST cache │    └─────────────────┘    │  │ SqlBackend   │  │ │
│  └──────────────┘                            │  │ SparkBackend │  │ │
│                                              │  └──────────────┘  │ │
│                                              └────────────────────┘ │
│                                                                    │
│                          ┌──────────────────────┐                  │
│                          │  InvocationRecorder  │  ← 每次调用都过  │
│                          │    - record(rec)     │                  │
│                          │    - recent(caller)  │                  │
│                          └──────────────────────┘                  │
└────────────────────────────────────────────────────────────────────┘
```

### 2.1 CelCompiler

- 包：`com.ontos.rule.core.compiler`
- 职责：把 CEL 字符串编译成 `CompiledRule`（含 AST + 类型环境 + 变量集）
- **变量推断**：未显式声明变量时，从表达式正则提取标识符（排除 CEL 关键字 / 数字开头）
- **AST 缓存**：以 `(expression, declaredVars)` 为 key 缓存 `CompiledRule`；重复编译 O(1) 返回
- **特殊处理**：自动声明 `_now: TIMESTAMP` 变量，JvmBackend 求值时注入 `Instant.now()`，让 `time_within` 维度可用

### 2.2 ExecutionBackend

- 包：`com.ontos.rule.core.backend`
- 接口：`Backend kind()` · `boolean eval(rule, row)` · `default ViolationResult executeBatch(rule, target, hints)`
- **设计要点**：`executeBatch` 的 `target` 是 `Object`，由各 Backend 按自己接受的类型 cast：
  - `JvmBackend` → `Iterable<Map<String,Object>>`
  - `SqlBackend` → 自己有 `executeOnSql(rule, DataSource, table, dialect, hints)` 的具名签名
  - `SparkBackend` → 未来吃 `Dataset<Row>`

#### JvmBackend
- 包：`backend.jvm`
- 求值方式：用 `dev.cel.runtime.CelRuntime` 直接跑 AST
- 适用：< 10 万行；单行写时校验

#### SqlBackend
- 包：`backend.sql`
- 工作流：`CelToSqlTranslator` 把 AST 翻译成方言 SQL → 拼接到 `SELECT count(*), array_agg(...) FROM <table> WHERE NOT (<cel-as-sql>)` → 执行 → 拼回 `ViolationResult`
- 适用：10 万 ~ 10 亿行，下推到数据库执行
- **方言**：`backend.sql.dialect.*` 下 6 个 `SqlDialect` 实现（PostgreSQL / MySQL / Oracle / Hive / Impala / StarRocks），按 `dialectName` 由 `SqlDialects.of(name)` 工厂选

### 2.3 BackendRouter

- 包：`com.ontos.rule.core.router`
- 单方法 `route(rule, estimatedRows, hints) → Backend`
- 决策表：

  | 条件 | 选择 |
  |------|------|
  | `hint.backend != AUTO` | 遵从用户 hint |
  | `estimatedRows < 100_000` 或未知 | `JVM` |
  | `100_000 ≤ estimatedRows < 1_000_000_000` | `SQL` |
  | `≥ 1_000_000_000` | `SPARK`（Phase 2） |

  阈值是常量，未来按场景调。

### 2.4 InvocationRecorder

- 包：`com.ontos.rule.core.invocation`
- 接口：`record(rec)` · `recent(caller, limit)` · `countByCaller(caller)` · `clear()`
- 默认实现：`InMemoryInvocationRecorder` —— 一个有界（默认 5000）的 `ConcurrentLinkedDeque`
- **替换为持久化**：见 [docs/extending.md](extending.md#自定义-recorder)
- **每次** `DefaultRuleEngine.eval/execute/executeOnSql` 都会在 try/finally 里 record 一条 `InvocationRecord(caller, expression, backend, mode, latencyMs, success, …)`

### 2.5 DefaultRuleEngine

- 包：`com.ontos.rule.core.impl`
- 实现 `RuleEngine`，组合上面四件套
- 通过 `builder()` 创建，可注入：`caller`、`InvocationRecorder`、自定义 Backend 集
- 线程安全（编译器有 cache、recorder 是并发集合、Backend 无状态）

---

## 3. 数据流：一次 `engine.eval(...)` 的全过程

```
caller code
   │
   │  engine.eval(rule, row, ctx)
   ▼
DefaultRuleEngine
   │
   │  long t0 = System.nanoTime()
   │  try {
   │      Backend kind = router.route(rule, -1, hints);  // 单行 → JVM
   │      backend = backends.get(kind);
   │      result  = backend.eval(rule, row);             // ① 真正求值
   │  } catch (Exception e) {
   │      error = e;  throw;
   │  } finally {
   │      recorder.record(new InvocationRecord(           // ② 追溯
   │          ctx.caller(), rule.expression(), kind,
   │          Mode.EVAL, (System.nanoTime()-t0)/1_000_000,
   │          error == null, error?.getMessage()
   │      ));
   │  }
   ▼
return result
```

`execute` / `executeOnSql` 流程同形，区别只在 ① 调用的 Backend 方法和 mode 字段。

---

## 4. business 层在 core 之上做了什么

| 概念 | 所在层 | 干啥 |
|------|--------|------|
| `Rule` (`@Entity`) | business · domain | 规则在数据库的持久化形态：name / target / severity / checks (JSON) / version / status |
| `CheckSpec` | business · web.dto | 维度卡片入参形态：`{type: "range", params: {min:0, max:100}}` |
| `RuleTypeHandler` + `RuleTypeRegistry` | business · service | 把 N 个 `CheckSpec` 编译为单个 CEL 字符串（`(value >= 0) && (value <= 100)`），然后丢给 core 编译执行。**这一层把"维度"概念隔离在 business 内，core 完全不知道有 unique / range / pattern 这种东西** |
| `RuleRun` (`@Entity`) | business · domain | 一次批量执行的产出物：`ViolationResult` + 元信息（rule / dataSource / startedAt / latencyMs / violations） |
| `QualityScore` (`@Entity`) | business · domain | 5 维（completeness/validity/uniqueness/consistency/timeliness）评分快照，由 `QualityScoreScheduler` 定期算 |
| `JdbcDataSourceRegistry` | business · service | 让用户通过 REST API 动态注册外部数据源（MySQL / PG / Oracle / Hive），底层 HikariCP 连接池 |
| `MockDataSource` | business · service | 内置 mock 数据，方便开箱即用 |

**关键设计：core 不知道有"维度"这个概念。** business 层把维度卡片编译为 CEL 后才丢给 core，core 只看到一个 CEL 字符串。这样 core 可以独立用在没有"维度"语义的场景里。

---

## 5. 关键设计取舍

### 为什么用 CEL 而不是自己写 DSL？

- **沙盒安全**：CEL 设计上不允许循环、不能调用任意 Java 方法，跑不死、跑不出
- **可移植**：CEL 有 Go / Java / TS / Rust 实现，将来真要在前端跑校验也能复用同一份表达式
- **生态成熟**：Istio / Kubernetes 都在用，bug 少
- **可翻译**：CEL 的 AST 是结构化的，比自定义 DSL 翻译成 SQL 简单

### 为什么 JVM/SQL/Spark 三选一而不是单一引擎？

| 引擎 | 强在哪 | 弱在哪 |
|------|--------|--------|
| JVM | 单行/写时校验延迟极低；表达能力跟 CEL 一致 | 大数据扛不住 |
| SQL | 大数据下推到 DB，省网络 IO 和内存 | CEL 有些函数没法翻译（嵌套 lambda 等） |
| Spark | 真大数据；可做复杂窗口 | 启动慢，小场景过度 |

三者各擅其场，由 Router 透明切换，用户写一份 CEL。

### 为什么追溯放在 Core 而不是 Business？

集成方可能跳过 business 直接拿 core 当 SDK 用。如果追溯只在 business 写库，集成方就追溯不到。放在 core 就保证**任何调用都至少留下一条记录**（哪怕只是内存）。要持久化由集成方注入自己的 Recorder。

---

## 6. 测试约定

- `core` 用 JUnit 5 + AssertJ
- SQL Backend 用 H2 跑（`SqlBackendIntegrationTest`），保证 SQL 翻译/执行真正能跑通
- `BackendConsistencyTest` 用同一份规则同时跑 JvmBackend + SqlBackend，断言结果一致 —— 防止 Backend 之间语义漂移

跑测试：

```bash
mvn -pl ontos-rule-core test
mvn -pl ontos-rule-business test
```

---

## 7. 还没解决的事（已知 trade-off）

| 问题 | 当前怎么办 | 真正的解法（未来） |
|------|------------|--------------------|
| `unique` 维度在单行 CEL 里表达不了 | `UniqueHandler.compile()` 返回 `value != null` 占位，由 `ExecutionService` 识别 type=unique 走特殊 SQL `GROUP BY` 分支 | 给 CEL 加聚合扩展函数；或维度 Handler 暴露 SQL 直接产出能力 |
| SQL 方言之间小差异（`COALESCE` vs `IFNULL`、字符串引号等） | `SqlDialect` 接口里抽象，每个方言实现自己的 `quoteIdent` / `nullCoalesce` 等 | 继续沉淀 |
| Core 的 `recorder()` 默认实现存内存，重启丢 | Business 可以替换为 JPA 实现 | 见 [docs/extending.md](extending.md#自定义-recorder) |
| Spark Backend 还没做 | hints.backend = SPARK 时抛 UnsupportedOperationException | Phase 2 |

---

## 8. 延伸阅读

- [操作手册（docs/usage.md）](usage.md) — 怎么用
- [扩展开发（docs/extending.md）](extending.md) — 怎么加你自己的东西
- [CEL 规范](https://github.com/google/cel-spec/blob/master/doc/langdef.md) — DSL 全集
