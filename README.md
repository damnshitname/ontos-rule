# ONTOS Rule Engine

> 一个**业务无关、可插拔、可追溯**的 CEL 规则引擎。一份规则，三种执行方式：**JVM 单行 / SQL 下推 / Spark 分布式**。

<p>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-Apache--2.0-blue.svg"></a>
  <img alt="Java" src="https://img.shields.io/badge/Java-21-orange?logo=openjdk">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-3.3-green?logo=springboot">
  <img alt="Vue" src="https://img.shields.io/badge/Vue-3-42b883?logo=vuedotjs">
  <img alt="CEL" src="https://img.shields.io/badge/DSL-Google%20CEL-4285F4">
  <img alt="Status" src="https://img.shields.io/badge/status-active-success">
</p>

---

## 为什么是它

数据治理、风控、营销策略——这些场景的共同痛点是：
- **规则散落各处**：有的写在 Java if/else，有的塞在 SQL `CASE WHEN`，有的扔在前端校验
- **改一次发一次版**：业务方提个新规则，得排进开发迭代
- **跑不动大数据**：JVM 跑 10 亿行就是死，但 SQL 又表达不出复杂逻辑
- **改完查不到**：谁在什么时候用了这条规则？没人说得清

**ONTOS Rule Engine** 用一套 [Google CEL](https://github.com/google/cel-spec) 表达式作为规则语言，自动按数据规模在 **JVM / SQL / Spark** 三种后端之间路由，每次调用都被追溯记录。

```cel
temperature + tolerance < maxLimit && status in ['RUNNING','IDLE']
```

写一次，10 行也能跑，10 亿行也能跑，跑完知道是谁跑的。

---

## 项目架构

```
┌──────────────────────────────────────────────────────────────────┐
│                       上游平台（任意系统）                       │
│   - 你自己的微服务（Java SDK 直接集成）                          │
│   - 其他语言系统（通过 REST API）                                │
└─────────────────────────────┬────────────────────────────────────┘
                              │
              ┌───────────────┴──────────────────┐
              │                                  │
              ▼                                  ▼
   ┌─────────────────────┐         ┌──────────────────────────┐
   │  ontos-rule-web     │  HTTP   │  ontos-rule-business     │
   │  Vue3 + Element     ├────────►│  Spring Boot 3 REST 层   │
   │  规则管理 / Playground         │  Rule CRUD / 执行 / 评分 │
   └─────────────────────┘         └──────────┬───────────────┘
                                              │ 直接调用
                                              ▼
                            ┌──────────────────────────────────┐
                            │       ontos-rule-core            │
                            │   ────────────────────────────   │
                            │   RuleEngine (入口)              │
                            │     ├─ CelCompiler  (编译+缓存)  │
                            │     ├─ BackendRouter (路由)      │
                            │     ├─ ExecutionBackend          │
                            │     │    ├─ JvmBackend           │
                            │     │    ├─ SqlBackend ──┐       │
                            │     │    └─ SparkBackend │ P2    │
                            │     └─ InvocationRecorder (追溯) │
                            └────────────────┬─────────────────┘
                                             │
                  ┌──────────────────────────┴──────────────────────────┐
                  ▼                          ▼                          ▼
        ┌───────────────────┐     ┌────────────────────┐     ┌─────────────────┐
        │  Iterable<Map>    │     │  JDBC DataSource   │     │  Spark Dataset  │
        │  (< 10 万行)      │     │  (10 万 ~ 10 亿)   │     │  (> 10 亿, P2)  │
        └───────────────────┘     └────────────────────┘     └─────────────────┘
                                  PG · MySQL · Oracle ·
                                  Hive · Impala · StarRocks
```

---

## 模块一览

| 模块 | 类型 | 说明 |
|------|------|------|
| **`ontos-rule-core`** | Java 21 Lib（零 Spring 依赖） | 规则编译 + 多后端执行 + 调用追溯。**可独立打 jar 被任意 Java 系统集成**。 |
| **`ontos-rule-business`** | Spring Boot 3 应用 | REST API + 规则 CRUD + 数据源注册 + 维度评分 + 调度。基于 H2 文件库，零外部依赖即可跑起来。 |
| **`ontos-rule-web`** | Vue 3 + Element Plus + ECharts | 规则管理后台 / Playground / 评分看板 / 调用日志查询。 |

---

## 5 分钟跑起来

### 0. 前置

- JDK 21+
- Maven 3.9+
- Node.js 20+

### 1. 启动后端（含演示数据自动种入）

```bash
git clone https://github.com/damnshitname/ontos-rule.git
cd ontos-rule
mvn clean install -DskipTests
mvn -pl ontos-rule-business spring-boot:run
```

启动后：
- REST API：<http://localhost:8080/api/rules>
- H2 控制台：<http://localhost:8080/h2-console>（JDBC URL：`jdbc:h2:file:./data/ontosrule`，user `sa`，密码空）

### 2. 启动前端

```bash
cd ontos-rule-web
npm install
npm run dev
```

打开 <http://localhost:5173>。

### 3. 跑一条规则（curl）

```bash
# 创建一条规则
curl -X POST http://localhost:8080/api/rules \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "温度上限检查",
    "target": "Lot.temperature",
    "severity": "HIGH",
    "checks": [{"type":"range","params":{"max":100}}]
  }'

# 单行求值（注意 X-Caller-Id 用于追溯来源）
curl -X POST http://localhost:8080/api/rules/QR-0001/eval \
  -H 'Content-Type: application/json' \
  -H 'X-Caller-Id: src-my-platform' \
  -d '{"record":{"temperature":92}}'

# 查调用记录
curl 'http://localhost:8080/api/invocations?caller=src-my-platform&limit=10'
```

---

## 作为 Java SDK 集成

`ontos-rule-core` 是零 Spring 依赖的纯 Java 库，可被任意 Java 应用直接调用：

```xml
<dependency>
    <groupId>com.ontos</groupId>
    <artifactId>ontos-rule-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
import com.ontos.rule.core.RuleEngine;
import com.ontos.rule.core.impl.DefaultRuleEngine;
import com.ontos.rule.core.model.*;

// 1. 创建引擎（caller 标识用于追溯）
RuleEngine engine = DefaultRuleEngine.builder()
    .caller("src-my-platform")
    .build();

// 2. 编译一次（AST 缓存自动复用）
CompiledRule rule = engine.compile(
    "temperature + tolerance < maxLimit && status in ['RUNNING','IDLE']"
);

// 3a. 单行求值
boolean ok = engine.eval(rule, Map.of(
    "temperature", 92, "tolerance", 5,
    "maxLimit", 100, "status", "RUNNING"
));

// 3b. 批量执行（JVM Backend）
List<Map<String,Object>> rows = loadFromSomewhere();
ViolationResult result = engine.execute(rule, rows, ExecutionHints.auto());

// 3c. 批量执行（SQL Backend——下推到数据库）
ViolationResult result = engine.executeOnSql(
    rule, dataSource, "lot_main", "postgresql", ExecutionHints.sql()
);

// 4. 查调用追溯
List<InvocationRecord> records = engine.recorder().recent("src-my-platform", 100);
```

---

## 核心能力

| 能力 | 说明 |
|------|------|
| **CEL DSL** | Google CEL，沙盒安全、跨语言可移植、表达能力强 |
| **AST 缓存** | 编译一次，重复求值无解析开销 |
| **多后端路由** | `< 10 万行` → JVM；`10 万 ~ 10 亿行` → SQL；`> 10 亿行` → Spark（Phase 2） |
| **6 种 SQL 方言** | PostgreSQL / MySQL / Oracle / Hive / Impala / StarRocks |
| **CEL → SQL 翻译** | 支持 `&&` / `\|\|` / `in` / `matches` / `size` / `string()` / `timestamp()` / `duration()` / `_now` |
| **10 种内置维度** | not_null / not_blank / unique / pattern / range / length / enum / starts_with / time_within / cross_field |
| **5 维评分** | completeness · validity · uniqueness · consistency · timeliness |
| **调用追溯** | 每次 eval/execute 记录 caller / 表达式 / backend / 耗时 / 结果，可按来源审计 |
| **可插拔 Backend** | 实现 `ExecutionBackend` 即可接入新引擎（Flink/Trino/...） |
| **可插拔 Recorder** | 默认内存，可换持久化（DB / Kafka / OTLP）实现 |

---

## 文档导航

- 📐 [架构设计](docs/architecture.md) — Core 五大组件、数据流、设计取舍
- 📖 [操作手册](docs/usage.md) — REST API、SDK 用法、数据源注册、追溯查询、常见报错
- 🔌 [扩展开发](docs/extending.md) — 新增 Backend、自定义 Recorder、新增 SQL 方言、扩展维度 Handler
- 🤝 [贡献指南](CONTRIBUTING.md) — 开发环境、提交规范、PR 流程

---

## Roadmap

| 里程碑 | 范围 | 状态 |
|--------|------|------|
| **Phase 1** | CEL 编译 + JVM Backend + InvocationRecorder | ✅ 已完成 |
| **Phase 1** | SQL Backend（6 方言）+ CEL→SQL 翻译 + 数据源动态注册 | ✅ 已完成 |
| **Phase 1** | Business 层（Rule CRUD / 执行 / 评分 / 调度） | ✅ 已完成 |
| **Phase 1** | Web 前端（管理后台 / Playground / 评分看板） | ✅ 已完成 |
| **Phase 2** | Spark Backend（10 亿行+） | 📅 规划中 |
| **Phase 2** | InvocationRecorder JDBC / Kafka 实现 | 📅 规划中 |
| **Phase 2** | 规则版本管理 + 灰度发布 | 📅 规划中 |
| **Phase 3** | Flink 实时流 Backend | 💭 待评估 |

---

## 设计原则

1. **业务无关** — Core 不知道"批次""设备""客户"，只知道"表达式 + 数据 → 结果"
2. **单一 DSL** — 用户只学 CEL（Google 开源、沙盒安全、跨语言）
3. **多 Backend，一致语义** — 同一规则在 JVM / SQL / Spark 上跑，结果应一致
4. **可追溯** — 每次调用记录 caller / 表达式 / 结果 / 耗时，可按来源审计
5. **零 Spring 依赖（Core）** — Core 是普通 jar，可被任何 Java 应用集成
6. **可插拔** — Backend / Recorder / 维度 Handler / SQL 方言 都是接口

---

## Maintainers

- **tiny.he** \<xiaoyi.he@zetatech.com.cn\>

欢迎提 Issue 或 PR。提交前请先看 [CONTRIBUTING.md](CONTRIBUTING.md)。

---

## License

Apache License 2.0 — 详见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。
