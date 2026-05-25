# ontos-rule-web

ONTOS 规则引擎前端工程。

跟 `ontos-rule-core` / `ontos-rule-business` / `ontos-rule-prototype` 同级，
是真正用于联调和未来上线的前端代码（不是原型 HTML）。

## 技术栈

- **Vite 5** — 构建 & 开发服务器
- **Vue 3.5** — 框架，`<script setup>` + Composition API
- **TypeScript 5.6** — 类型安全，与后端 DTO 对齐
- **Element Plus 2.8** — UI 组件库（自动按需导入）
- **Pinia 2** — 状态管理（暂时只是装好，还没用到）
- **Vue Router 4** — 路由
- **axios** — HTTP 客户端，统一处理错误 + 自动注入 `X-Caller-Id`

## 目录结构

```
ontos-rule-web/
├── index.html              # Vite 入口 HTML
├── package.json
├── tsconfig.json
├── vite.config.ts          # 含 /api 代理 → :8080
├── src/
│   ├── main.ts             # 应用启动入口
│   ├── App.vue             # 根组件 + 顶栏 + 主题切换
│   ├── router/             # vue-router 配置
│   ├── api/                # axios 实例 + 按业务划分的 API 模块
│   │   ├── client.ts       # 统一 axios 实例 + 错误拦截
│   │   ├── rules.ts        # /api/rules/*
│   │   └── quality.ts      # /api/quality/*
│   ├── types/
│   │   ├── domain.ts       # 与后端 DTO 对齐的类型
│   │   ├── shims-vue.d.ts  # *.vue 模块声明
│   │   ├── auto-imports.d.ts   # vite 插件自动生成（已 gitignore）
│   │   └── components.d.ts     # vite 插件自动生成（已 gitignore）
│   ├── views/              # 路由级页面
│   │   ├── OverviewView.vue    # 首页：项目总分 + 对象排行
│   │   ├── RulesView.vue       # 规则列表 + 一键执行
│   │   ├── QualityView.vue     # 维度热力图
│   │   └── RunsView.vue        # 执行历史（占位，待后端接口）
│   ├── components/         # 通用组件（暂空）
│   ├── stores/             # Pinia stores（暂空）
│   └── styles/
│       └── tokens.css      # 与 ontos-rule-prototype 一致的设计 token
└── public/                 # 静态资源
```

## 快速开始

```bash
cd ontos-rule-web
npm install
npm run dev
# 访问 http://localhost:5173
```

需要后端先起来（`mvn -pl ontos-rule-business -am spring-boot:run`，监听 `:8080`），
否则首页加载会报错。Vite 已配置 `/api/*` → `http://localhost:8080` 代理，跨域不用操心。

## 与后端的契约

| 前端调用 | 后端 Controller |
|---|---|
| `rulesApi.list/get/create/update/remove/execute` | `RuleController` / `ExecutionController` |
| `qualityApi.projectScore/heatmap/objectScore/recompute/getWeights/setWeights` | `QualityScoreController` |

后端 DTO 字段变动时，同步修改 `src/types/domain.ts`，TypeScript 编译期会暴露所有受影响的调用点。

## 设计 Token

`src/styles/tokens.css` 与 `../ontos-rule-prototype/assets/tokens.css` **完全一致**，
且通过覆写 `--el-color-primary` / `--el-bg-color` 等让 Element Plus 默认主题贴近 ONTOS 工业风。

切换主题（顶栏右上角按钮）会同时：
1. 把 `<html data-theme>` 切换到 `dark` / `light` → 触发 token 切换
2. 把 `<html class="dark">` 切换 → 触发 Element Plus 暗色主题
3. 写入 `localStorage`，下次进入恢复偏好

## 待完成

- [ ] 规则**新建/编辑**对话框（带表单 / CEL 切换）
- [ ] 执行历史列表（需后端先暴露 `GET /api/runs`）
- [ ] 维度权重配置 UI（后端接口已就绪：`PUT /api/quality/weights`）
- [ ] 规则详情侧抽屉（违规明细 + 调用追溯）
- [ ] 雷达图（用 ECharts 替代当前的进度条堆叠展示）
- [ ] 路由守卫 / 401 处理（暂无鉴权）
