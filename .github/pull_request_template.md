<!--
PR 提交前请确认：
- 提交信息遵循 Conventional Commits（见 CONTRIBUTING.md）
- 关联了对应 Issue（如有）
- mvn test 通过；改了前端的话 npm run build 也通过
-->

## 改动说明

<!-- 用一两句话说清楚做了什么。why > what，what 看 diff 就行。 -->

## 关联 Issue

Closes #

## 类型

- [ ] 🐛 Bug fix
- [ ] ✨ New feature
- [ ] 💥 Breaking change（修改了 `RuleEngine` / `ExecutionBackend` / `InvocationRecorder` 公开接口）
- [ ] 📖 Docs only
- [ ] 🔧 Refactor / perf / chore

## 自查清单

- [ ] 跑过 `mvn test`，通过
- [ ] 若改了前端：`npm run type-check && npm run build` 通过
- [ ] 若新增 Backend / Dialect：补了 `BackendConsistencyTest` 用例
- [ ] 若改了公开接口：在文档（architecture/usage/extending）同步更新
- [ ] 若加了新配置项：在 `docs/usage.md` 表格里补了说明
- [ ] 没有 commit 进 `target/` / `node_modules/` / `dist/` / `.env*` / `*.local`
- [ ] 没有把测试用的真实数据库连接串提交进来

## 测试结果

<!-- 贴你跑的命令和输出，或截图 -->

```
mvn -pl ontos-rule-core test
[INFO] Tests run: ...
```

## 额外说明

<!-- 设计决策、已知限制、下一步计划 -->
