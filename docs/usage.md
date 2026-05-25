# Usage · 操作手册

面向**集成方**：怎么把 ONTOS Rule Engine 用起来。两条路：
1. **REST API** —— 你不写 Java 也能用，curl/Python/Node 任何客户端都行
2. **Java SDK** —— 直接把 `ontos-rule-core` 当库引

---

## 目录

- [1. 启动后端](#1-启动后端)
- [2. REST API 速查](#2-rest-api-速查)
- [3. 规则 CRUD](#3-规则-crud)
- [4. 单行求值（eval）](#4-单行求值eval)
- [5. 批量执行（execute）](#5-批量执行execute)
- [6. 数据源管理](#6-数据源管理)
- [7. 调用追溯查询](#7-调用追溯查询)
- [8. Java SDK 用法](#8-java-sdk-用法)
- [9. CEL 速查表](#9-cel-速查表)
- [10. 常见报错排查](#10-常见报错排查)

---

## 1. 启动后端

```bash
mvn -pl ontos-rule-business spring-boot:run
```

默认端口 `8080`。配置在 `ontos-rule-business/src/main/resources/application.yml`：

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `server.port` | 8080 | HTTP 端口 |
| `spring.datasource.url` | H2 文件 `./data/ontosrule` | 改为 `jdbc:h2:mem:...` 即转内存模式 |
| `ontos.rule.default-caller` | `src-rest-api` | REST 调用未带 `X-Caller-Id` header 时的默认 caller |
| `ontos.rule.invocation-recorder-max-size` | 5000 | 内存调用记录上限 |

> ⚠️ **生产部署提示**：H2 文件 + JPA `ddl-auto: update` 仅适合 demo。生产建议换 PostgreSQL / MySQL，并改用 Flyway/Liquibase 管理 schema。

---

## 2. REST API 速查

| 资源 | 方法 + 路径 | 说明 |
|------|-------------|------|
| **规则** | `POST   /api/rules` | 创建 |
|  | `GET    /api/rules` | 列表 |
|  | `GET    /api/rules/{id}` | 详情 |
|  | `PUT    /api/rules/{id}` | 更新 |
|  | `DELETE /api/rules/{id}` | 删除 |
| **执行** | `POST   /api/rules/{ruleId}/eval` | 单行求值 |
|  | `POST   /api/rules/{ruleId}/execute` | 批量执行 |
| **运行历史** | `GET    /api/runs` | 列表 |
|  | `GET    /api/runs/{id}` | 详情（含违规明细） |
| **数据源** | `GET    /api/data-sources` | 列出所有可用源 |
|  | `POST   /api/data-sources/jdbc` | 注册 JDBC 源 |
|  | `POST   /api/data-sources/jdbc/{name}/seed-demo` | 在指定源建演示表 |
|  | `DELETE /api/data-sources/jdbc/{name}` | 注销 JDBC 源 |
| **调用追溯** | `GET    /api/invocations` | 最近调用 |
|  | `GET    /api/invocations/stats` | 按 caller/backend/mode 聚合 |
| **评分** | `GET    /api/quality-scores/latest` | 最新 5 维评分 |
|  | `GET    /api/quality-scores/history` | 评分历史曲线 |
| **Playground** | `POST   /api/playground/eval` | 即兴试规则（不入库） |
| **Admin** | `POST   /api/admin/reseed` | 一键清空 + 重新种入演示数据（生产建议关闭） |

所有 POST 接受 `application/json`。所有调用支持 `X-Caller-Id` header，用于追溯来源；不传则用 `application/yml` 中 `default-caller`。

---

## 3. 规则 CRUD

### 创建

```bash
curl -X POST http://localhost:8080/api/rules \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "温度上限校验",
    "target": "Lot.temperature",
    "severity": "HIGH",
    "description": "炉温不能超过 100",
    "checks": [
      {"type": "not_null", "params": {}},
      {"type": "range",    "params": {"min": 0, "max": 100}}
    ]
  }'
```

返回：

```json
{
  "id": "QR-0007",
  "name": "温度上限校验",
  "target": "Lot.temperature",
  "severity": "HIGH",
  "checks": [...],
  "compiledExpression": "(value != null) && (value >= 0 && value <= 100)",
  "dimensions": ["completeness", "validity"],
  "status": "ACTIVE",
  "version": 1,
  "createdAt": "2026-05-25T10:21:00Z"
}
```

注意 `compiledExpression` 是 business 层把多个 `CheckSpec` 编译出的 CEL 字符串。`value` 是占位符，运行时会按 `target` 后半段（这里是 `temperature`）替换成真实列名。

### 支持的 check type（10 种内置维度）

| type | params | 维度 | 示例 |
|------|--------|------|------|
| `not_null` | — | completeness | `value != null` |
| `not_blank` | — | completeness | `value != null && size(value) > 0` |
| `unique` | — | uniqueness | 走 SQL `GROUP BY` 特殊分支 |
| `pattern` | `regex` | validity | `matches(value, "...")` |
| `range` | `min`, `max`（至少一个） | validity | `value >= min && value <= max` |
| `length` | `min`, `max`（至少一个） | validity | `size(value) >= min && size(value) <= max` |
| `enum` | `allowed`（数组或逗号串） | validity | `value in ["A","B","C"]` |
| `starts_with` | `prefix` | validity | `string(value).startsWith("...")` |
| `time_within` | `days` | timeliness | `_now - timestamp(value) < duration("Nh")` |
| `cross_field` | `left`, `op`, `right` | consistency | `fieldA op fieldB` |

要扩展？看 [extending.md · 扩展维度 Handler](extending.md#扩展维度-handler)。

### 查询

```bash
curl http://localhost:8080/api/rules
curl http://localhost:8080/api/rules/QR-0007
```

### 更新 / 删除

```bash
curl -X PUT http://localhost:8080/api/rules/QR-0007 \
  -H 'Content-Type: application/json' \
  -d '{"severity":"CRITICAL"}'

curl -X DELETE http://localhost:8080/api/rules/QR-0007
```

---

## 4. 单行求值（eval）

```bash
curl -X POST http://localhost:8080/api/rules/QR-0007/eval \
  -H 'Content-Type: application/json' \
  -H 'X-Caller-Id: src-my-platform' \
  -d '{"record": {"temperature": 92, "tolerance": 5, "maxLimit": 100}}'
```

返回：

```json
{
  "result": true,
  "latencyMs": 3,
  "backend": "JVM",
  "caller": "src-my-platform"
}
```

`result = true` 表示**通过**校验；`false` 表示**违规**。

---

## 5. 批量执行（execute）

```bash
curl -X POST http://localhost:8080/api/rules/QR-0007/execute \
  -H 'Content-Type: application/json' \
  -H 'X-Caller-Id: src-my-platform' \
  -d '{"dataSource": "mock-lot"}'
```

`dataSource` 取值：
- `mock-lot` / `mock-customer` / ... —— 内置 mock 数据，走 JVM Backend
- 任何用 `POST /api/data-sources/jdbc` 注册过的名字 —— 走 SQL Backend

返回 `RuleRunDto`：

```json
{
  "id": "RUN-1024",
  "ruleId": "QR-0007",
  "dataSource": "mock-lot",
  "backend": "JVM",
  "status": "COMPLETED",
  "totalRows": 100,
  "passedRows": 97,
  "failedRows": 3,
  "latencyMs": 41,
  "startedAt": "2026-05-25T10:25:30Z",
  "violations": [
    {"rowIndex": 12, "fields": {...}},
    ...
  ]
}
```

查历史：

```bash
curl http://localhost:8080/api/runs
curl http://localhost:8080/api/runs/RUN-1024
```

---

## 6. 数据源管理

### 列出当前可用

```bash
curl http://localhost:8080/api/data-sources
```

```json
{
  "jvm-mock":  ["mock-lot", "mock-customer", "mock-orders"],
  "sql-jdbc":  []
}
```

### 注册 JDBC 数据源

```bash
curl -X POST http://localhost:8080/api/data-sources/jdbc \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "prod-pg",
    "jdbcUrl": "jdbc:postgresql://localhost:5432/mydb",
    "username": "ro_user",
    "password": "***",
    "tableName": "lot_main",
    "dialect": "postgresql"
  }'
```

`dialect` 取值：`postgresql` / `mysql` / `oracle` / `hive` / `impala` / `starrocks`。

注册成功后 `dataSource: "prod-pg"` 就能在 `execute` 里用了。

> 🛡 **安全提示**：注册接口暴露在 `/api/data-sources/jdbc`，密码以明文 POST 进来。生产环境建议：
> 1. 用 Spring Security 给 `/api/data-sources/**` 加鉴权
> 2. 走 HTTPS
> 3. 改为读 Vault / 配置中心，而不是 API 注册

### 注销

```bash
curl -X DELETE http://localhost:8080/api/data-sources/jdbc/prod-pg
```

会关闭对应的 HikariCP 连接池。

### 在 JDBC 源里种演示数据

```bash
curl -X POST http://localhost:8080/api/data-sources/jdbc/prod-pg/seed-demo
```

按方言适配建表 SQL 创建 `lot_main` 表 + 插 100 行测试数据。仅 demo 用。

---

## 7. 调用追溯查询

每次 `eval` / `execute` 都会被 `InvocationRecorder` 记一条。

### 列出最近

```bash
curl 'http://localhost:8080/api/invocations'                          # 默认最近 50
curl 'http://localhost:8080/api/invocations?caller=src-my-platform'   # 按来源筛
curl 'http://localhost:8080/api/invocations?limit=500'                # 调上限
```

### 按 caller / backend / mode 聚合

```bash
curl http://localhost:8080/api/invocations/stats
```

```json
{
  "totalInvocations": 1283,
  "successCount": 1271,
  "failedCount": 12,
  "byCaller":  {"src-my-platform": 920, "src-rest-api": 363},
  "byBackend": {"JVM": 1100, "SQL": 183},
  "byMode":    {"EVAL": 1240, "EXECUTE": 43}
}
```

> 默认是**内存**记录，重启丢。要持久化看 [extending.md · 自定义 Recorder](extending.md#自定义-recorder)。

---

## 8. Java SDK 用法

把 core 当库引：

```xml
<dependency>
    <groupId>com.ontos</groupId>
    <artifactId>ontos-rule-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 最小例子

```java
RuleEngine engine = DefaultRuleEngine.builder()
    .caller("src-my-platform")
    .build();

CompiledRule rule = engine.compile("a + b < c");
boolean ok = engine.eval(rule, Map.of("a", 1, "b", 2, "c", 5));
// ok == true
```

### 批量（JVM Backend）

```java
List<Map<String,Object>> rows = List.of(
    Map.of("temperature", 80),
    Map.of("temperature", 110),
    Map.of("temperature", 95)
);

CompiledRule rule = engine.compile("temperature <= 100");
ViolationResult result = engine.execute(rule, rows, ExecutionHints.auto());

System.out.println(result.totalRows());     // 3
System.out.println(result.failedRows());    // 1
System.out.println(result.violations());    // [{rowIndex:1, fields:{temperature:110}}]
```

### 批量（SQL Backend，下推到数据库）

```java
HikariDataSource ds = new HikariDataSource();
ds.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
ds.setUsername("ro_user");
ds.setPassword("***");

CompiledRule rule = engine.compile("status in ['RUNNING','IDLE']");
ViolationResult result = engine.executeOnSql(
    rule, ds, "lot_main", "postgresql", ExecutionHints.sql()
);
```

### 显式控制 Backend

```java
ExecutionHints jvm  = ExecutionHints.jvm();    // 强制 JVM
ExecutionHints sql  = ExecutionHints.sql();    // 强制 SQL
ExecutionHints auto = ExecutionHints.auto();   // 让 Router 自动选
```

### 注入自定义调用上下文

```java
InvocationContext ctx = InvocationContext.of("src-batch-job-2026-05-25");
engine.eval(rule, row, ctx);
```

---

## 9. CEL 速查表

| 想做的事 | CEL 写法 | 备注 |
|----------|----------|------|
| 比较 | `a > 10`, `b == "RUNNING"` | |
| 逻辑 | `a > 0 && b < 100`, `x \|\| y` | |
| 包含 | `value in ["A","B","C"]` | |
| 字符串前缀 | `string(value).startsWith("PRE-")` | cel-java 不支持自由函数 `startsWith(s,p)` |
| 字符串长度 | `size(value) > 0` | |
| 正则 | `matches(value, "^[A-Z]{2}-\\d+$")` | |
| 类型转换 | `string(123)`, `int("42")`, `double(x)` | |
| 时间窗口 | `_now - timestamp(value) < duration("24h")` | `_now` 由 JvmBackend 注入；`duration` 只用小时 `Nh`，不用 `Nd` |
| 三元 | `a > 0 ? "pos" : "neg"` | |
| 空安全 | `has(obj.field)` | |

完整规范：<https://github.com/google/cel-spec/blob/master/doc/langdef.md>

---

## 10. 常见报错排查

| 报错 | 原因 | 怎么修 |
|------|------|--------|
| `CompilationException: undeclared reference to 'xxx'` | CEL 引用了变量但没声明 | 用 `engine.compile(expr, Set.of("xxx", ...))` 显式声明；或确保表达式里出现的是合法的标识符（CelCompiler 会自动从表达式正则抽变量） |
| `executeOnSql ... TranslationException` | 某个 CEL 函数还没翻译实现 | 见 `CelToSqlTranslator` 已支持函数清单；不支持的函数走 JVM Backend |
| `Backend SPARK 未实现批量执行` | hints.backend = SPARK 但 SparkBackend 还没做 | 改 `ExecutionHints.auto()` 或 `ExecutionHints.sql()` |
| `未知维度 type: xxx` | business 的 `CheckSpec.type` 写错 | 看 `RuleTypeRegistry` 源码确认 type 名（10 种内置见上方维度表） |
| `range 至少需要 min 或 max 之一` | params 全空 | 至少填一个 |
| H2 控制台连不上 | `web-allow-others: false` | 改 `application.yml` → `web-allow-others: true`（仅 dev） |
| `Address already in use: bind` | 8080 被占 | 改 `application.yml` 里 `server.port` |
| `Could not resolve dependencies for project ... cel` | Maven 仓库没拉到 CEL 包 | 镜像问题，加阿里云镜像；或检查网络 |
| 启动失败 `Java 8 instead of 21` | 用了 IDEA 自带的 mvn（JDK 1.8） | 在系统装 JDK 21 + Maven 3.9，或在 IDEA Project Structure 切 SDK 21 |

---

## 11. 调试技巧

- 看 SQL Backend 实际生成的 SQL：把 `logging.level` 里 `com.ontos` 调成 `TRACE`
- 看 invocation 详情：`GET /api/invocations?limit=1`，每条记录都带原始表达式 + caller + 异常 message
- 在 IDEA 里跑单测：直接 run `RuleEngineTest` / `BackendConsistencyTest`

---

## 12. 下一步

- 想自己加 Backend / Recorder / 维度？看 [extending.md](extending.md)
- 想理解内部架构？看 [architecture.md](architecture.md)
- 想贡献 PR？看 [CONTRIBUTING.md](../CONTRIBUTING.md)
