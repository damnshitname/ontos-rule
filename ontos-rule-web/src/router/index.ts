import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import CubePlatformLayout from '@/components/CubePlatformLayout.vue'

const routes: RouteRecordRaw[] = [
  // Core 调试沙箱 · 独立全屏（不走 Cube 平台 Layout）
  {
    path: '/playground',
    name: 'playground',
    component: () => import('@/views/PlaygroundView.vue'),
  },
  // 业务层 · Cube 数据质量平台嵌入
  {
    path: '/',
    component: CubePlatformLayout,
    children: [
      { path: '', redirect: '/score' },
      { path: 'rules', name: 'rules', component: () => import('@/views/RulesView.vue') },
      { path: 'runs', name: 'runs', component: () => import('@/views/RunsView.vue') },
      { path: 'score', name: 'score', component: () => import('@/views/ScoreView.vue') },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
