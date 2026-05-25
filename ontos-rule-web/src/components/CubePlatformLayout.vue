<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { rulesApi } from '@/api/rules'
import { runsApi } from '@/api/runs'
import { qualityApi } from '@/api/quality'

const route = useRoute()
const router = useRouter()

const now = ref(new Date())
setInterval(() => (now.value = new Date()), 1000)
const timeStr = computed(() => now.value.toTimeString().slice(0, 8))

const ruleCount = ref(0)
const violationCount = ref(0)
const projectScore = ref<number | null>(null)
const projectGrade = ref<string>('')
const projectScoreDelta = ref<number | null>(null)
const violationDelta = ref<number | null>(null)

async function refreshMetrics() {
  try {
    const [rules, runs, proj, history] = await Promise.all([
      rulesApi.list().catch(() => []),
      runsApi.list({ limit: 200 }).catch(() => []),
      qualityApi.projectScore().catch(() => ({ project: null, objects: [] })),
      qualityApi.projectHistory(14).catch(() => []),
    ])
    ruleCount.value = rules.length
    violationCount.value = runs.reduce((sum, r) => sum + (r.violationCount ?? 0), 0)
    projectScore.value = proj.project?.overallScore ?? null
    projectGrade.value = proj.project?.grade ?? ''

    // 7 日 delta：找最接近 7 天前的快照（seeder 是周采样）
    const sevenDaysAgo = Date.now() - 7 * 24 * 3600 * 1000
    const olderSnaps = history.filter(
      (s) => new Date(s.snapshotAt).getTime() <= sevenDaysAgo
    )
    const closest = olderSnaps[olderSnaps.length - 1]
    if (projectScore.value != null && closest) {
      projectScoreDelta.value = projectScore.value - Number(closest.overallScore)
      const oldViols = Number(closest.violationsCount ?? 0)
      violationDelta.value = violationCount.value - oldViols
    } else {
      projectScoreDelta.value = null
      violationDelta.value = null
    }
  } catch {}
}

onMounted(refreshMetrics)
defineExpose({ refreshMetrics })

// 暴露给子页面调用
;(window as any).__refreshMetrics = refreshMetrics

const tabs = [
  { key: 'rules', path: '/rules', label: '规则', badge: () => ruleCount.value },
  { key: 'runs', path: '/runs', label: '执行历史', badge: () => null },
  { key: 'score', path: '/score', label: '质量评分', badge: () => projectScore.value?.toFixed(1) },
]

function activeTab(): string {
  const p = route.path
  if (p.startsWith('/runs')) return 'runs'
  if (p.startsWith('/score') || p === '/') return 'score'
  return 'rules'
}

function goTab(t: typeof tabs[number]) {
  router.push(t.path)
}

function openPlayground() {
  window.open('/playground', '_blank')
}

</script>

<template>
  <div class="cube-shell">
    <!-- ========= 顶栏 40px sticky ========= -->
    <header class="cube-header">
      <div class="flex items-center gap-2">
        <div class="logo-mark">C</div>
        <div class="font-mono font-semibold" style="font-size:12px;letter-spacing:0.1em;text-transform:uppercase;">CUBE</div>
        <span class="label-sm" style="color:var(--fg-3)">数据质量平台 · v0.3</span>
      </div>

      <div class="customer-chip">
        <span class="dot dot-ok dot-live"></span>
        <span class="text-sm">SkyTech</span>
        <span class="label-sm">prod-fab1</span>
        <span class="fg-2 text-xs">▾</span>
      </div>

      <div class="flex-1" style="max-width: 480px; margin: 0 auto;">
        <input class="input input-mono" placeholder="搜索对象 / 字段 / Action / 规则... ⌘K" />
      </div>

      <div class="flex items-center gap-3 fg-2 text-xs font-mono" style="border-left:1px solid var(--bg-4); padding-left: 12px;">
        <div><span class="dot dot-ok"></span> SYS OK</div>
        <div>2 Worker</div>
        <div>{{ timeStr }}</div>
      </div>

      <button class="btn btn-ghost btn-sm">通知 <span class="tag tag-warn ml-2">3</span></button>
      <select class="select" style="width: auto;">
        <option>ENG · 张工</option>
        <option>ANALYST · 王经理</option>
      </select>
    </header>

    <!-- ========= 嵌入提示条 ========= -->
    <div class="embed-banner">
      <span class="label fg-info">嵌入演示</span>
      <span class="fg-1">
        这是 <strong style="color:var(--fg-0)">规则引擎</strong> 嵌入
        <strong style="color:var(--fg-0)">Cube 数据质量平台</strong> 后的形态 ·
        复用顶栏 + 左 nav · 仅 "规则引擎" 入口可点 · 其他 nav 项灰显作参考
      </span>
    </div>

    <!-- ========= 主体：侧栏 + 内容 ========= -->
    <div class="cube-body">
      <!-- 侧栏 -->
      <aside class="cube-aside">
        <div class="nav-section">概览</div>
        <div class="nav-item nav-item-dim"><span class="label-sm nav-icon">HM</span> 工作台</div>

        <div class="nav-section">数据接入</div>
        <div class="nav-item nav-item-dim"><span class="label-sm nav-icon">DS</span> 数据源</div>
        <div class="nav-item nav-item-dim"><span class="label-sm nav-icon">SC</span> 调度采集</div>

        <div class="nav-section">核心能力</div>
        <div class="nav-item nav-item-dim"><span class="label-sm nav-icon">MP</span> 智能映射</div>
        <div class="nav-item nav-item-dim"><span class="label-sm nav-icon">MD</span> 元数据管理</div>
        <div class="nav-item nav-item-active">
          <span class="label-sm nav-icon">RL</span> 规则引擎
          <span class="tag tag-info ml-auto">{{ ruleCount }}</span>
        </div>

        <div class="nav-section">运营</div>
        <div class="nav-item nav-item-dim"><span class="label-sm nav-icon">EX</span> 对象探索</div>
        <div class="nav-item nav-item-dim"><span class="label-sm nav-icon">AC</span> Action</div>
        <div class="nav-item nav-item-dim"><span class="label-sm nav-icon">LN</span> 血缘浏览器</div>
        <div class="nav-item nav-item-dim"><span class="label-sm nav-icon">GV</span> 数据治理</div>

        <div class="aside-divider"></div>
        <div class="nav-item nav-item-dim"><span class="label-sm nav-icon">CFG</span> 设置</div>

        <div class="aside-footer">
          <div class="flex items-center justify-between fg-2"><span>Worker</span><span class="fg-ok">2/2</span></div>
          <div class="flex items-center justify-between fg-2"><span>已运行</span><span class="fg-0">14d 7h</span></div>
          <div class="flex items-center justify-between fg-2"><span>许可</span><span class="fg-warn">剩 358 天</span></div>
        </div>
      </aside>

      <!-- 内容区 -->
      <main class="cube-main">
        <!-- 面包屑 + 操作 -->
        <div class="main-head">
          <div class="flex items-center gap-3">
            <span class="label">核心能力</span>
            <span class="fg-3">/</span>
            <span class="text-sm">规则引擎</span>
            <span class="tag tag-info ml-2">QUALITY · 试点</span>
          </div>
          <div class="flex items-center gap-3 text-xs font-mono">
            <span class="label">由 RULE-CORE 0.1.0 驱动</span>
            <button class="btn btn-sm" @click="openPlayground">[ 调试沙箱 → ]</button>
          </div>
        </div>

        <!-- 5 个 metric 横排 -->
        <div class="metrics-bar">
          <div class="metric metric-clickable" @click="router.push('/rules')">
            <div class="metric-l">活跃规则</div>
            <div class="metric-v">{{ ruleCount }}</div>
            <div class="metric-d">已注册 · 点击查看</div>
          </div>
          <div class="metric metric-clickable" @click="router.push('/runs')">
            <div class="metric-l">累计违规</div>
            <div class="metric-v fg-warn">
              {{ violationCount }}
              <span v-if="violationDelta != null" class="delta"
                    :class="violationDelta > 0 ? 'delta-down' : 'delta-up'">
                {{ violationDelta > 0 ? '▲' : '▼' }}{{ Math.abs(violationDelta) }}
              </span>
            </div>
            <div class="metric-d">最近 200 次 · {{ violationDelta != null ? 'vs 7 天前' : '执行历史' }}</div>
          </div>
          <div class="metric">
            <div class="metric-l">覆盖对象</div>
            <div class="metric-v">{{ projectScore != null ? 1 : 0 }}<span class="metric-u">/ N</span></div>
            <div class="metric-d">Lot / Equipment / Wafer ...</div>
          </div>
          <div class="metric metric-clickable" @click="router.push('/score')">
            <div class="metric-l">项目总分</div>
            <div class="metric-v" :class="projectGrade === 'A+' || projectGrade === 'A' ? 'fg-ok' : projectGrade === 'B' ? 'fg-warn' : 'fg-err'">
              {{ projectScore != null ? projectScore.toFixed(1) : '—' }}
              <span v-if="projectScoreDelta != null" class="delta"
                    :class="projectScoreDelta >= 0 ? 'delta-up' : 'delta-down'">
                {{ projectScoreDelta >= 0 ? '▲' : '▼' }}{{ Math.abs(projectScoreDelta).toFixed(1) }}
              </span>
            </div>
            <div class="metric-d">{{ projectGrade || '未评' }}{{ projectScoreDelta != null ? ' · vs 7 天前' : '' }}</div>
          </div>
          <el-tooltip placement="bottom" effect="dark">
            <template #content>
              <div style="max-width: 280px; line-height: 1.6;">
                <div><b>CORE 引擎可用的执行后端</b></div>
                <div style="margin-top: 4px;">· <b>JVM</b>: 单条求值，亚毫秒级（PPT 实测 ~380K rows/s）</div>
                <div>· <b>SQL × 7</b>: 7 种方言下推（PG / MySQL / Oracle / Hive / Impala / StarRocks / Kudu），亿级数据可推到 DB 跑</div>
                <div style="margin-top: 4px;"><b>28 一致性测试</b> = JVM 与 SQL 跨后端语义对齐的 CI 门禁（同规则两个后端结果必须 100% 一致）</div>
              </div>
            </template>
            <div class="metric" style="cursor: help;">
              <div class="metric-l">CORE 后端</div>
              <div class="metric-v" style="font-size:14px; margin-top: 4px;">JVM · SQL × 7</div>
              <div class="metric-d fg-ok">28 一致性测试 ✓</div>
            </div>
          </el-tooltip>
        </div>

        <!-- Tabs -->
        <div class="px-3">
          <div class="tabs">
            <div
              v-for="t in tabs" :key="t.key"
              class="tab" :class="{ 'tab-active': activeTab() === t.key }"
              @click="goTab(t)"
            >
              {{ t.label }}
              <template v-if="t.badge() != null && t.badge() !== ''">
                &middot; {{ t.badge() }}
              </template>
            </div>
          </div>
        </div>

        <!-- 当前 Tab 内容 -->
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.cube-shell {
  min-height: 100vh; display: flex; flex-direction: column;
  background: var(--bg-0);
}

/* 顶栏 */
.cube-header {
  display: flex; align-items: center;
  height: 40px; padding: 0 12px;
  background: var(--bg-1); border-bottom: 1px solid var(--bg-4);
  position: sticky; top: 0; z-index: 30; gap: 12px;
}
.logo-mark {
  width: 22px; height: 22px;
  background: var(--accent);
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 12px; color: #fff;
  font-family: var(--font-mono);
}
.customer-chip {
  display: flex; align-items: center; gap: 8px;
  padding: 0 12px; cursor: pointer;
  border-left: 1px solid var(--bg-4); height: 40px;
}

/* 嵌入提示条 */
.embed-banner {
  background: var(--info-dim); border-bottom: 1px solid var(--info);
  padding: 6px 16px; display: flex; align-items: center; gap: 12px;
  font-size: 11.5px;
}

/* 主体 */
.cube-body {
  display: flex;
  min-height: calc(100vh - 70px);
}

/* 侧栏 */
.cube-aside {
  width: 200px; background: var(--bg-1);
  border-right: 1px solid var(--bg-4);
  flex-shrink: 0; position: relative;
}
.nav-icon { width: 24px; color: var(--fg-3); flex-shrink: 0; }
.aside-divider {
  border-top: 1px solid var(--bg-4); margin-top: 16px;
}
.aside-footer {
  position: absolute; bottom: 0; width: 200px;
  padding: 8px 12px;
  background: var(--bg-2); border-top: 1px solid var(--bg-4);
  font-family: var(--font-mono); font-size: 10px;
}

/* 主内容 */
.cube-main {
  flex: 1; overflow-x: hidden; min-width: 0;
  background: var(--bg-0);
}
.main-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px; border-bottom: 1px solid var(--bg-4);
  background: var(--bg-1);
}
.metrics-bar {
  display: grid; grid-template-columns: repeat(5, 1fr);
  gap: 1px; background: var(--bg-4); margin: 12px;
}
.metrics-bar .metric { border: none; position: relative; }
.metric-clickable {
  cursor: pointer;
  transition: background 0.12s;
}
.metric-clickable:hover {
  background: var(--bg-2);
}
.metric-clickable::after {
  content: '→';
  position: absolute; top: 8px; right: 10px;
  color: var(--fg-3); font-family: var(--font-mono); font-size: 11px;
  opacity: 0; transition: opacity 0.12s;
}
.metric-clickable:hover::after { opacity: 1; }
.delta {
  font-family: var(--font-mono); font-size: 11px;
  margin-left: 6px; font-weight: 500;
  vertical-align: middle;
}
.delta-up { color: var(--ok); }
.delta-down { color: var(--err); }
</style>
