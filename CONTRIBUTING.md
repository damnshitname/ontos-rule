# 贡献指南

欢迎给 **ONTOS Rule Engine** 提 Issue 或 PR。这份文档帮你少走弯路。

## 行为准则

平等、尊重、就事论事。不接受人身攻击、骚扰、歧视性言论。

---

## 你可以贡献什么

- 🐛 **Bug 修复** —— 直接提 PR，附最小复现
- 💡 **新特性** —— 先开 Issue 讨论方向，避免做完了发现方向不对
- 📖 **文档改进** —— 错别字 / 不清晰的地方 / 缺示例 都欢迎
- 🔌 **新 Backend / Dialect / RuleTypeHandler** —— 看 [docs/extending.md](docs/extending.md)
- ✅ **测试补全** —— 尤其是 `BackendConsistencyTest` 的用例

---

## 开发环境

### 必备

| 工具 | 版本 |
|------|------|
| JDK | 21+ |
| Maven | 3.9+ |
| Node.js | 20+ |
| Git | 2.40+ |

> ⚠️ IDEA 自带的 Maven 可能跑在 JDK 8 上，会编译失败。要么装系统 Maven，要么在 IDEA `Project Structure` 把 Project SDK 切到 21。

### 克隆 & 跑起来

```bash
git clone https://github.com/damnshitname/ontos-rule.git
cd ontos-rule

# 后端
mvn clean install -DskipTests
mvn -pl ontos-rule-business spring-boot:run

# 前端（另一个终端）
cd ontos-rule-web
npm install
npm run dev
```

### 跑测试

```bash
mvn test                              # 全部
mvn -pl ontos-rule-core test          # 只跑 core
mvn -pl ontos-rule-core test -Dtest=BackendConsistencyTest   # 单个测试类
```

### 前端构建

```bash
cd ontos-rule-web
npm run type-check
npm run build
```

---

## 分支策略

- `main` —— 始终可发布的状态
- 功能分支：`feat/<short-name>` · 修复：`fix/<short-name>` · 文档：`docs/<short-name>`

不要直接 push `main`，走 PR。

---

## 提交规范（Conventional Commits）

```
<type>(<scope>): <subject>

<body, optional>

<footer, optional>
```

`type` 取值：

| type | 用法 |
|------|------|
| `feat` | 新功能 |
| `fix` | bug 修复 |
| `docs` | 仅文档 |
| `refactor` | 不改变行为的重构 |
| `perf` | 性能 |
| `test` | 测试 |
| `build` | 构建系统 / 依赖 |
| `ci` | CI 配置 |
| `chore` | 杂项 |

`scope` 推荐用模块名：`core` / `business` / `web` / `docs` / 具体子目录。

### 例子

```
feat(core): add ClickhouseDialect for SQL backend

实现 SqlDialect 接口的所有方法，包含正则、字符串前缀、size 翻译。
在 SqlDialects.of() 工厂注册。

Closes #42
```

```
fix(business): UniqueHandler 在多列情况下漏过 NULL

之前 `value != null` 占位会让 SQL GROUP BY 把 NULL 也算成一组，
违背了 unique 维度的语义。改为在 ExecutionService 的特殊分支里
直接生成 HAVING count(*) > 1 AND col IS NOT NULL。
```

### 反例（别这样写）

```
update    ← 改了啥？
fix bug   ← 什么 bug？
WIP       ← 进库的不应该是 WIP
```

---

## PR 流程

1. **开 Issue 先讨论**（特性类）—— 避免做完了发现方向不对
2. 从 `main` 拉分支
3. 实现 + **写测试** + 跑 `mvn test` 通过
4. 跑 `mvn -pl ontos-rule-web ...` 的 type-check 通过（如果改了前端）
5. 提 PR，套用模板，关联 Issue
6. CI 通过 + 至少 1 个 reviewer approve → 合并

### Review 要点

- 是否破坏 `RuleEngine` / `ExecutionBackend` / `InvocationRecorder` 公开接口？破坏的话标 BREAKING CHANGE
- 是否加了/改了 Backend？必须有 `BackendConsistencyTest` 覆盖
- 是否引入新依赖？说明为什么必要
- 文档同步更新了吗？

---

## 测试要求

- 新代码**覆盖率不低于 80%**（参考值，重要分支必须覆盖）
- 关键不变量必须有断言：
  - `BackendConsistencyTest` —— 同一规则在不同 Backend 跑出一致结果
  - `SqlDialect` 子类 —— 字符串转义必须有 SQL 注入测试用例
  - `InvocationRecorder` 子类 —— 并发 record 不丢数据

---

## 文档要求

- 改了公开 API → 改 `docs/architecture.md` 和 / 或 `docs/usage.md`
- 新增扩展点 → 改 `docs/extending.md`
- 改了 REST API → 改 `docs/usage.md`
- 新增配置项 → 改 `docs/usage.md` 的 application.yml 表格

---

## 提 Issue 前

- 先搜一下有没有重复
- Bug：附最小复现代码 + 期望行为 + 实际行为 + 环境（JDK / OS）
- Feature：说清楚 use case 和现状的痛点，再说方案

---

## 安全报告

发现安全漏洞**别开 public Issue**。私下发邮件到 `xiaoyi.he@zetatech.com.cn`，主题前缀 `[SECURITY]`。
我们会在 7 天内回复，确认后 90 天内披露。

---

## License

提交 PR 即表示你同意你的贡献以 [Apache License 2.0](LICENSE) 发布。
