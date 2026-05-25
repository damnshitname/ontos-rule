# Extending · 扩展开发指南

这份文档告诉你怎么往 ONTOS Rule Engine 里**加自己的东西**而不动 Core 的接口。
四个最常见的扩展点：

| 想做的事 | 实现哪个接口 | 难度 |
|----------|---------------|------|
| 接一个新执行引擎（Flink/Trino/...） | `ExecutionBackend` | 中 |
| 把调用记录持久化到 DB / Kafka / OTLP | `InvocationRecorder` | 低 |
| 加一种 SQL 方言（ClickHouse / Doris...） | `SqlDialect` | 低 |
| 加一个新维度（business 层） | `RuleTypeHandler` | 低 |

---

## 1. 新增一个 Backend

### 1.1 接口契约

```java
package com.ontos.rule.core.backend;

public interface ExecutionBackend {
    Backend kind();
    boolean eval(CompiledRule rule, Map<String, Object> record);
    default ViolationResult executeBatch(CompiledRule rule, Object target, ExecutionHints hints) {
        throw new UnsupportedOperationException(...);
    }
}
```

`kind()` 返回的 `Backend` 是个枚举，目前是 `JVM / SQL / SPARK / AUTO`。
要加新值时直接在 `com.ontos.rule.core.model.Backend` 里加一个枚举常量（不向后兼容，但属于 minor 改动）。

### 1.2 例子：实现一个 TrinoBackend

```java
package com.ontos.rule.core.backend.trino;

public class TrinoBackend implements ExecutionBackend {
    @Override public Backend kind() { return Backend.TRINO; }   // 假设已加枚举值

    @Override
    public boolean eval(CompiledRule rule, Map<String, Object> record) {
        // 单行求值场景不适合 Trino——直接走 JVM 实现兜底
        return new JvmBackend().eval(rule, record);
    }

    @Override
    public ViolationResult executeBatch(CompiledRule rule, Object target, ExecutionHints hints) {
        // target 约定为 TrinoConnection 或 (TrinoConnection, tableName) 的元组
        // 1. 用 CelToSqlTranslator 把 AST 翻译成 Trino 方言 SQL
        // 2. 用 PreparedStatement 跑 SELECT count(*), array_agg(...) FROM <table> WHERE NOT (<cel-as-sql>)
        // 3. 拼回 ViolationResult
        ...
    }
}
```

### 1.3 注册进引擎

```java
RuleEngine engine = DefaultRuleEngine.builder()
    .caller("src-my-platform")
    .registerBackend(new TrinoBackend())
    .build();
```

或者改 `BackendRouter` 让它在某些场景自动选 Trino：

```java
public Backend route(CompiledRule rule, long estimatedRows, ExecutionHints hints) {
    if (hints.backend() != Backend.AUTO) return hints.backend();
    if (hints.preferDistributed() && estimatedRows > LARGE_THRESHOLD) {
        return Backend.TRINO;
    }
    // ... 原逻辑
}
```

### 1.4 一致性测试

强烈建议在 `core/src/test/java/.../backend/BackendConsistencyTest` 里给你的 Backend 加用例：**同一份 CEL + 同一份测试数据，JvmBackend 和 TrinoBackend 的结果必须一致**。Backend 之间语义漂移是这个项目最致命的 bug。

---

## 2. 自定义 Recorder

默认 `InMemoryInvocationRecorder` 重启就丢，生产想持久化分三步。

### 2.1 实现接口

```java
public class JdbcInvocationRecorder implements InvocationRecorder {
    private final JdbcTemplate jdbc;

    public JdbcInvocationRecorder(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void record(InvocationRecord rec) {
        jdbc.update("""
            INSERT INTO rule_invocations(
                caller, expression, backend, mode,
                latency_ms, success, error_msg, recorded_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            rec.context().caller(), rec.expression(),
            rec.backend().name(), rec.mode().name(),
            rec.latencyMs(), rec.isSuccess(),
            rec.errorMessage(), Timestamp.from(rec.recordedAt())
        );
    }

    @Override
    public List<InvocationRecord> recent(String caller, int limit) { ... }

    @Override
    public long countByCaller(String caller) { ... }

    @Override
    public void clear() { jdbc.update("TRUNCATE rule_invocations"); }
}
```

### 2.2 注入到引擎

```java
@Configuration
public class RuleEngineConfig {

    @Bean
    public InvocationRecorder invocationRecorder(JdbcTemplate jdbc) {
        return new JdbcInvocationRecorder(jdbc);
    }

    @Bean
    public RuleEngine ruleEngine(InvocationRecorder recorder) {
        return DefaultRuleEngine.builder()
            .caller("src-rest-api")
            .recorder(recorder)
            .build();
    }
}
```

### 2.3 异步包一层（性能敏感）

```java
public class AsyncInvocationRecorder implements InvocationRecorder {
    private final InvocationRecorder delegate;
    private final Executor pool = Executors.newSingleThreadExecutor(...);

    @Override public void record(InvocationRecord rec) {
        pool.execute(() -> delegate.record(rec));
    }
    // recent/countByCaller/clear 同步透传
}
```

记得给 pool 一个有界队列，避免 OOM 拖死主调用链路。

### 2.4 其它常见 Recorder 实现思路

| 目标 | 思路 |
|------|------|
| **Kafka** | `record()` 里 `producer.send(new ProducerRecord("rule-invocations", json))`；`recent()` 从下游消费者查询 |
| **OpenTelemetry** | 每条 record 转成 span event；`latencyMs` 当 span 持续时间 |
| **Prometheus** | 不持久化记录本身，只 `counter.labels(caller, backend).inc()` + `histogram.observe(latencyMs)` |

---

## 3. 新增 SQL 方言

要加 ClickHouse / Doris / SQL Server / BigQuery 等：

### 3.1 实现 `SqlDialect`

```java
package com.ontos.rule.core.backend.sql.dialect;

public class ClickhouseDialect implements SqlDialect {
    @Override public String name() { return "clickhouse"; }

    /** 标识符引用，例如 "`column`" 或 "[column]" */
    @Override public String quoteIdent(String ident) {
        return "`" + ident.replace("`", "``") + "`";
    }

    /** 空合并函数 */
    @Override public String nullCoalesce(String expr, String fallback) {
        return "ifNull(" + expr + ", " + fallback + ")";
    }

    /** 正则匹配函数 —— CEL matches(s, p) 翻译目标 */
    @Override public String matchesRegex(String value, String pattern) {
        return "match(" + value + ", " + pattern + ")";
    }

    /** 字符串前缀 —— CEL string(value).startsWith(p) 翻译目标 */
    @Override public String startsWith(String value, String prefix) {
        return "startsWith(" + value + ", " + prefix + ")";
    }

    /** "size(x)" 翻译 —— 字符串长度或数组长度 */
    @Override public String size(String expr) {
        return "length(" + expr + ")";
    }

    // 其它按 SqlDialect 接口定义补齐
}
```

### 3.2 在工厂注册

`SqlDialects.of(String name)` 是工厂入口。在那里加一行 case：

```java
case "clickhouse" -> new ClickhouseDialect();
```

### 3.3 用上

```bash
curl -X POST http://localhost:8080/api/data-sources/jdbc \
  -d '{
    "name": "ch-prod",
    "jdbcUrl": "jdbc:clickhouse://ch.example:8123/default",
    ...
    "dialect": "clickhouse"
  }'
```

### 3.4 别忘了测试

在 `core/src/test/.../backend/sql/dialect/ClickhouseDialectTest` 至少跑：
- `matches` 翻译
- `in [...]` 翻译
- 引号转义（含单引号注入测试）
- 日期/时间字面量

---

## 4. 扩展维度 Handler（business 层）

这是 **business** 层的扩展，不动 core。

### 4.1 实现 `RuleTypeHandler`

```java
package com.ontos.rule.biz.service;

public class RegexNotMatchHandler implements RuleTypeHandler {
    public String type() { return "regex_not_match"; }
    public String displayName() { return "正则不匹配"; }
    public String dimension() { return "validity"; }

    public void validate(Map<String, Object> p) {
        if (p.get("regex") == null) throw new IllegalArgumentException("需要 regex 参数");
    }

    public String compile(Map<String, Object> p) {
        String regex = p.get("regex").toString()
            .replace("\\", "\\\\").replace("\"", "\\\"");
        return "!matches(value, \"" + regex + "\")";   // 注意取反
    }
}
```

### 4.2 在 `RuleTypeRegistry` 注册

```java
public RuleTypeRegistry() {
    register(new NotNullHandler());
    // ... 其它内置
    register(new RegexNotMatchHandler());   // ← 加这一行
}
```

### 4.3 前端补卡片配置

`ontos-rule-web/src/components/RuleEditorDialog.vue` 里的 `CHECK_TYPES` 元数据加一项：

```ts
{
  type: 'regex_not_match',
  label: '正则不匹配',
  dimension: 'validity',
  params: [
    { key: 'regex', label: '正则', type: 'string', required: true }
  ]
}
```

### 4.4 验证

```bash
curl -X POST http://localhost:8080/api/rules \
  -d '{
    "name": "禁用敏感词",
    "target": "Customer.name",
    "checks": [{"type":"regex_not_match","params":{"regex":"敏感|违禁"}}]
  }'

curl -X POST http://localhost:8080/api/rules/QR-xxxx/eval \
  -d '{"record":{"name":"包含敏感词的名字"}}'
# 应当返回 {"result": false, ...}
```

---

## 5. 怎么改 Router 路由策略

`BackendRouter` 现在只看 `estimatedRows`。要加更多决策维度（比如基于规则复杂度 / 数据源能力），有两种做法：

### 做法 A：扩展现有 `BackendRouter`

直接改 `route()`，加你自己的判断分支：

```java
public Backend route(CompiledRule rule, long estimatedRows, ExecutionHints hints) {
    if (hints.backend() != Backend.AUTO) return hints.backend();

    // 新增：如果规则包含 windowed 聚合函数（未来），强制走 Flink
    if (rule.metadata().contains(Feature.WINDOWED_AGG)) {
        return Backend.FLINK;
    }

    // 原逻辑...
}
```

### 做法 B：把 Router 抽成接口（推荐，但需要 minor 重构）

```java
public interface RoutingStrategy {
    Backend choose(CompiledRule rule, long estimatedRows, ExecutionHints hints);
}

public class DefaultRoutingStrategy implements RoutingStrategy { ... }
public class CostBasedRoutingStrategy implements RoutingStrategy { ... }
```

然后 `DefaultRuleEngine.builder().router(...)` 让用户注入。这块还没做，PR 欢迎。

---

## 6. 给 CEL 加自定义函数（高阶）

CEL 是可扩展的——可以注册自定义函数。在 `CelCompiler` 构造时：

```java
CelCompiler celCompiler = CelCompilerFactory.standardCelCompilerBuilder()
    .addFunctionDeclarations(
        CelFunctionDecl.newFunctionDeclaration(
            "isWorkday",
            CelOverloadDecl.newGlobalOverload(
                "isWorkday_string",
                SimpleType.BOOL,
                SimpleType.TIMESTAMP
            )
        )
    )
    .build();

CelRuntime celRuntime = CelRuntimeFactory.standardCelRuntimeBuilder()
    .addFunctionBindings(
        CelFunctionBinding.from(
            "isWorkday_string", Instant.class,
            (Instant t) -> {
                DayOfWeek dow = t.atZone(ZoneId.systemDefault()).getDayOfWeek();
                return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
            }
        )
    )
    .build();
```

然后 CEL 表达式里就能写 `isWorkday(timestamp(eventTime))`。

⚠️ **重要**：自定义函数只在 JVM Backend 生效。SQL Backend 要么给 `CelToSqlTranslator` 补对应翻译规则，要么把这条规则的 hints 强制成 JVM。

---

## 7. 提一个 PR 之前

- 跑全量测试：`mvn test`
- 跑前端构建：`cd ontos-rule-web && npm run build`
- 若加了 Backend 或方言：补一致性测试
- 若改了公开接口（`RuleEngine` / `ExecutionBackend` / `InvocationRecorder`）：在 PR 描述里标 **BREAKING CHANGE**
- 看 [CONTRIBUTING.md](../CONTRIBUTING.md) 的提交规范

---

## 8. 更多扩展点

下面这些 Core 还没抽接口，但**已经在路线图上**。有兴趣可以发 Issue 讨论后做：

| 扩展点 | 现状 | 路线 |
|--------|------|------|
| `RoutingStrategy` | 写死在 `BackendRouter` | 抽接口，Phase 2 |
| `ExpressionCache` | 写死 ConcurrentHashMap | 抽接口，可换 Caffeine / Redis |
| `RuleStorage` | business 用 JPA | 抽 SPI，可换 ETCD / Consul |
| 多租户 | 单实例无租户隔离 | 在 InvocationContext 加 tenant 字段 |
