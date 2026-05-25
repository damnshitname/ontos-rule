<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { runsApi, type ViolationDto } from '@/api/runs'
import { rulesApi } from '@/api/rules'
import type { RuleRunDto, RuleDto } from '@/types/domain'
import EmptyState from '@/components/EmptyState.vue'

const loading = ref(false)
const runs = ref<RuleRunDto[]>([])
const rules = ref<RuleDto[]>([])
const filterRuleId = ref<string>('')
const limit = ref<number>(50)

const drawerOpen = ref(false)
const drawerLoading = ref(false)
const drawerRun = ref<RuleRunDto | null>(null)
const drawerViolations = ref<ViolationDto[]>([])

async function load() {
  loading.value = true
  try {
    runs.value = await runsApi.list({
      ruleId: filterRuleId.value || undefined,
      limit: limit.value,
    })
  } finally { loading.value = false }
}

async function loadRules() { rules.value = await rulesApi.list() }

async function openDrawer(row: RuleRunDto) {
  drawerRun.value = row
  drawerOpen.value = true
  drawerLoading.value = true
  drawerViolations.value = []
  try { drawerViolations.value = await runsApi.violations(row.id) }
  finally { drawerLoading.value = false }
}

function fmtDuration(ms?: number): string {
  if (ms == null) return '—'
  if (ms < 1000) return ms + ' ms'
  return (ms / 1000).toFixed(2) + ' s'
}

function violationRate(row: RuleRunDto): string {
  if (!row.totalRows || row.totalRows === 0) return '—'
  return (((row.violationCount ?? 0) * 100) / row.totalRows).toFixed(2) + '%'
}

function statusTag(s?: string) {
  if (s === 'SUCCESS') return 'ok'
  if (s === 'FAILED') return 'err'
  return 'idle'
}

const totalRuns = computed(() => runs.value.length)
const totalViolations = computed(() => runs.value.reduce((s, r) => s + (r.violationCount ?? 0), 0))
const totalRows = computed(() => runs.value.reduce((s, r) => s + (r.totalRows ?? 0), 0))
const totalDuration = computed(() => runs.value.reduce((s, r) => s + (r.durationMs ?? 0), 0))

async function reload() { await loadRules(); await load() }
onMounted(() => {
  reload()
  window.addEventListener('ontos-reseed', reload)
})
onUnmounted(() => {
  window.removeEventListener('ontos-reseed', reload)
})
</script>

<template>
  <div class="runs-tab">
    <!-- 顶部统计 + 操作 -->
    <div class="actions-row">
      <span class="label">最近 {{ totalRuns }} 次执行</span>
      <span class="fg-3 mx-1">·</span>
      <span class="text-xs fg-2 font-mono">违规累计: <span class="fg-warn">{{ totalViolations }}</span></span>
      <span class="fg-3 mx-1">·</span>
      <span class="text-xs fg-2 font-mono">扫描数据: <span class="fg-0">{{ totalRows.toLocaleString() }}</span> 行</span>
      <span class="fg-3 mx-1">·</span>
      <span class="text-xs fg-2 font-mono">总耗时: <span class="fg-0">{{ fmtDuration(totalDuration) }}</span></span>

      <select class="select ml-auto" v-model="filterRuleId" @change="load" style="width: 220px;">
        <option value="">全部规则</option>
        <option v-for="r in rules" :key="r.id" :value="r.id">{{ r.id }} · {{ r.name }}</option>
      </select>
      <input class="input" type="number" v-model.number="limit" @change="load"
             min="10" max="500" step="10" style="width: 80px;" />
      <button class="btn btn-sm" @click="load" :disabled="loading">↻ 刷新</button>
    </div>

    <!-- 执行历史表 -->
    <div class="panel" style="margin: 12px;">
      <div class="panel-header">
        <span class="label">RUNS · 执行历史</span>
        <span class="ml-auto label-sm fg-2">点击行查看违规明细</span>
      </div>
      <table class="t">
        <thead>
          <tr>
            <th>#</th>
            <th>规则</th>
            <th>数据源</th>
            <th>状态</th>
            <th>BACKEND</th>
            <th class="num">扫描</th>
            <th class="num">违规</th>
            <th class="num">违规率</th>
            <th class="num">耗时</th>
            <th>调用方</th>
            <th>开始时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="run in runs" :key="run.id" class="cursor-pointer" @click="openDrawer(run)">
            <td class="font-mono text-xs fg-2">{{ run.id }}</td>
            <td><span class="font-mono text-xs">{{ run.ruleId }}</span></td>
            <td><span class="font-mono text-xs fg-2">{{ run.dataSource || '—' }}</span></td>
            <td><span class="tag" :class="`tag-${statusTag(run.status)}`">{{ run.status }}</span></td>
            <td><span class="tag tag-info">{{ run.backendUsed || '—' }}</span></td>
            <td class="num font-mono text-xs">{{ run.totalRows ?? '—' }}</td>
            <td class="num">
              <span class="font-mono text-xs" :class="(run.violationCount ?? 0) > 0 ? 'fg-warn font-medium' : 'fg-ok'">
                {{ run.violationCount ?? '—' }}
              </span>
            </td>
            <td class="num font-mono text-xs fg-2">{{ violationRate(run) }}</td>
            <td class="num font-mono text-xs">{{ fmtDuration(run.durationMs) }}</td>
            <td class="text-xs fg-2 font-mono">{{ run.caller || '—' }}</td>
            <td class="font-mono text-xs fg-2">{{ run.startedAt }}</td>
          </tr>
          <tr v-if="!loading && runs.length === 0">
            <td colspan="11" style="padding: 0;">
              <EmptyState
                icon="◷"
                title="还没有执行历史"
                subtitle="去【规则】Tab 点 [ run ] 执行一条规则；或顶栏 [↺ 重置 demo 数据] 加载演示数据"
              />
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 违规明细侧抽屉 -->
    <el-drawer v-model="drawerOpen" size="60%"
               :title="`执行 #${drawerRun?.id} · ${drawerRun?.ruleId}`">
      <template #default>
        <div v-if="drawerRun" class="drawer-meta panel">
          <div class="panel-header"><span class="label">EXECUTION META</span></div>
          <div class="p-3" style="display: grid; grid-template-columns: 1fr 1fr; gap: 4px 24px; font-size: 11px;">
            <div class="kv"><span class="kv-k">规则</span><span class="kv-v">{{ drawerRun.ruleId }}</span></div>
            <div class="kv"><span class="kv-k">数据源</span><span class="kv-v">{{ drawerRun.dataSource }}</span></div>
            <div class="kv"><span class="kv-k">BACKEND</span><span class="kv-v">{{ drawerRun.backendUsed }}</span></div>
            <div class="kv"><span class="kv-k">状态</span><span class="kv-v">{{ drawerRun.status }}</span></div>
            <div class="kv"><span class="kv-k">扫描总数</span><span class="kv-v">{{ drawerRun.totalRows }}</span></div>
            <div class="kv"><span class="kv-k">违规数</span><span class="kv-v fg-err">{{ drawerRun.violationCount }}</span></div>
            <div class="kv"><span class="kv-k">违规率</span><span class="kv-v">{{ violationRate(drawerRun) }}</span></div>
            <div class="kv"><span class="kv-k">耗时</span><span class="kv-v">{{ fmtDuration(drawerRun.durationMs) }}</span></div>
            <div class="kv"><span class="kv-k">调用方</span><span class="kv-v">{{ drawerRun.caller }}</span></div>
            <div class="kv"><span class="kv-k">开始时间</span><span class="kv-v">{{ drawerRun.startedAt }}</span></div>
          </div>
        </div>

        <div class="callout callout-warn" style="margin: 12px 0;" v-if="drawerRun?.violationCount">
          <span class="label fg-warn">{{ drawerRun.violationCount }} 条违规</span>
          <span class="ml-2 text-xs">采样上限 1000 条 · 完整违规可通过 SQL Backend 直接查询</span>
        </div>

        <div class="panel">
          <div class="panel-header">
            <span class="label">VIOLATIONS · 违规明细</span>
            <span class="ml-auto label-sm fg-2">显示 {{ drawerViolations.length }} 条</span>
          </div>
          <table class="t t-tight" v-loading="drawerLoading">
            <thead>
              <tr>
                <th style="width: 30px;"></th>
                <th>主键</th>
                <th>违规值 (摘要)</th>
                <th>采样时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="v in drawerViolations" :key="v.id">
                <td><span class="dot dot-err"></span></td>
                <td class="font-mono text-xs">{{ v.targetPk }}</td>
                <td class="font-mono text-xs fg-1" style="max-width: 480px; word-break: break-all;">{{ v.violatingValue }}</td>
                <td class="font-mono text-xs fg-2">{{ v.sampledAt }}</td>
              </tr>
              <tr v-if="!drawerLoading && drawerViolations.length === 0">
                <td colspan="4" style="text-align: center; padding: 30px 0;" class="fg-ok">✓ 本次执行无违规</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.actions-row {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px;
}
.mx-1 { margin: 0 4px; }
.drawer-meta { margin-bottom: 12px; }
</style>
