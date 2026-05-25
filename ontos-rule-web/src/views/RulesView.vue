<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { rulesApi } from '@/api/rules'
import type { RuleDto } from '@/types/domain'
import RuleEditorDialog from '@/components/RuleEditorDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import RunResultToast from '@/components/RunResultToast.vue'

const runToast = ref<InstanceType<typeof RunResultToast> | null>(null)

const loading = ref(false)
const rules = ref<RuleDto[]>([])
const dialogOpen = ref(false)
const editingRule = ref<RuleDto | null>(null)
const page = ref(1)
const pageSize = ref(10)
const search = ref('')

const filteredRules = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return rules.value
  return rules.value.filter((r) =>
    r.id.toLowerCase().includes(q) ||
    r.name.toLowerCase().includes(q) ||
    (r.target || '').toLowerCase().includes(q) ||
    (r.owner || '').toLowerCase().includes(q) ||
    (r.expression || '').toLowerCase().includes(q)
  )
})

const pagedRules = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredRules.value.slice(start, start + pageSize.value)
})

// 搜索变化时回第 1 页
watch(search, () => { page.value = 1 })

async function load() {
  loading.value = true
  try { rules.value = await rulesApi.list() } finally { loading.value = false }
  ;(window as any).__refreshMetrics?.()
}

function openCreate() { editingRule.value = null; dialogOpen.value = true }
function openEdit(row: RuleDto) { editingRule.value = row; dialogOpen.value = true }

async function onSaved() { await load() }

async function execute(row: RuleDto, ev: MouseEvent) {
  ev.stopPropagation()
  const ds = row.target?.startsWith('Lot') ? 'mock-lot'
    : row.target?.startsWith('Equipment') ? 'mock-equipment'
    : 'mock-wafer'
  runToast.value?.start(row.id, row.name, ds)
  try {
    const run = await rulesApi.execute(row.id, ds)
    runToast.value?.done(run)
    await load()
  } catch (e: any) {
    runToast.value?.fail(e?.response?.data?.message || e?.message || '执行失败')
  }
}

async function removeRule(row: RuleDto, ev: MouseEvent) {
  ev.stopPropagation()
  const ok = await ElMessageBox.confirm(`确定删除规则 ${row.id}？`, '删除确认', { type: 'warning' })
    .catch(() => false)
  if (ok === false) return
  await rulesApi.remove(row.id)
  ElMessage.success('已删除')
  await load()
}

function severityTag(s?: string) { return s === 'ERROR' ? 'err' : 'warn' }
function statusTag(s?: string) {
  if (s === 'ACTIVE') return 'ok'
  if (s === 'DISABLED') return 'idle'
  return 'warn'
}
function expressionPreview(r: RuleDto) {
  const exp = r.expression || ''
  return exp.length > 60 ? exp.slice(0, 60) + '…' : exp
}

onMounted(() => {
  load()
  window.addEventListener('ontos-reseed', load)
})
onUnmounted(() => {
  window.removeEventListener('ontos-reseed', load)
})
</script>

<template>
  <div class="rules-tab">
    <!-- 顶部操作栏 -->
    <div class="actions-row">
      <button class="btn btn-pri btn-sm" @click="openCreate">+ 新建规则</button>
      <button class="btn btn-sm" @click="load" :disabled="loading">↻ 刷新</button>
      <div class="search-wrap">
        <span class="search-icon">⌕</span>
        <input
          v-model="search"
          class="search-input"
          placeholder="按 ID / 名称 / Target / 负责人 / 表达式 模糊匹配"
        />
        <span v-if="search" class="search-clear" @click="search = ''">×</span>
      </div>
      <span class="ml-auto label fg-2">
        {{ search ? `${filteredRules.length} / ${rules.length}` : rules.length }} 条规则
      </span>
    </div>

    <!-- 规则表格 -->
    <div class="panel" style="margin: 12px;">
      <table class="t">
        <thead>
          <tr>
            <th>ID</th>
            <th>名称 / 形态 + 表达式</th>
            <th>目标</th>
            <th>严重度</th>
            <th>BACKEND</th>
            <th>状态</th>
            <th class="num">维度</th>
            <th>负责人</th>
            <th class="text-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in pagedRules" :key="r.id" class="cursor-pointer" @click="openEdit(r)">
            <td class="font-mono text-xs fg-2">{{ r.id }}</td>
            <td class="text-xs" style="max-width: 360px;">
              <div class="font-medium">{{ r.name }}</div>
              <div class="fg-3 label-sm" style="margin-top: 2px; display: flex; align-items: center; gap: 4px;">
                <span class="tag" :class="r.mode === 'FORM' ? 'tag-info' : 'tag-warn'">{{ r.mode === 'FORM' ? '表单' : 'CEL' }}</span>
                <span class="font-mono truncate" style="font-size: 10px;">{{ expressionPreview(r) }}</span>
              </div>
            </td>
            <td><span class="font-mono text-xs">{{ r.target }}</span></td>
            <td><span class="tag" :class="`tag-${severityTag(r.severity)}`">{{ r.severity === 'ERROR' ? 'ERR' : 'WARN' }}</span></td>
            <td><span class="tag tag-info">{{ r.backendHint }}</span></td>
            <td><span class="tag" :class="`tag-${statusTag(r.status)}`">{{ r.status }}</span></td>
            <td class="num text-xs font-mono fg-1">{{ r.dimensions || '—' }}</td>
            <td class="text-xs fg-2">{{ r.owner || '—' }}</td>
            <td class="text-right">
              <button class="btn btn-xs" @click="execute(r, $event)">[ run ]</button>
              <button class="btn btn-xs" @click.stop="openEdit(r)">[ edit ]</button>
              <button class="btn btn-xs" @click="removeRule(r, $event)" style="color: var(--err);">[ × ]</button>
            </td>
          </tr>
          <tr v-if="!loading && filteredRules.length === 0">
            <td colspan="9" style="padding: 0;">
              <EmptyState
                v-if="rules.length === 0"
                icon="∅"
                title="还没有任何规则"
                subtitle="点击下方按钮一键加载演示数据，或右上角新建规则"
                @reseeded="load"
              />
              <div v-else class="empty-search">
                <div style="font-size: 32px; color: var(--fg-3);">⌕</div>
                <div style="margin-top: 8px; color: var(--fg-1);">
                  没有匹配 "<span class="font-mono">{{ search }}</span>" 的规则
                </div>
                <button class="btn btn-sm" style="margin-top: 12px;" @click="search = ''">[ 清除搜索 ]</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="pager-row" v-if="filteredRules.length > 0">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="filteredRules.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
          small
        />
      </div>
    </div>

    <!-- 提示 callout -->
    <div class="callout" style="margin: 12px;">
      <span class="label fg-info">提示</span>
      <span class="ml-2">
        规则有两种形式：<span class="tag tag-info">维度卡片</span> 勾选即用 ·
        <span class="tag tag-warn">CEL</span> 自由表达式 · 编辑器内左右并排，底部选择保存为哪种
      </span>
    </div>

    <RuleEditorDialog v-model="dialogOpen" :editing="editingRule" @saved="onSaved" />
    <RunResultToast ref="runToast" />
  </div>
</template>

<style scoped>
.actions-row {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px;
}
.search-wrap {
  position: relative; margin-left: 8px;
}
.search-input {
  background: var(--bg-0); border: 1px solid var(--bg-4);
  color: var(--fg-0); padding: 4px 28px 4px 26px;
  font-size: 12px; font-family: var(--font-sans);
  width: 320px; outline: none; height: 26px;
}
.search-input:focus { border-color: var(--accent); }
.search-icon {
  position: absolute; left: 8px; top: 50%; transform: translateY(-50%);
  color: var(--fg-3); font-size: 14px; pointer-events: none;
}
.search-clear {
  position: absolute; right: 8px; top: 50%; transform: translateY(-50%);
  color: var(--fg-3); cursor: pointer; font-size: 14px; line-height: 1;
}
.search-clear:hover { color: var(--fg-0); }
.pager-row {
  display: flex; justify-content: flex-end;
  padding: 8px 12px; border-top: 1px solid var(--bg-3);
  background: var(--bg-2);
}
.empty-search {
  text-align: center; padding: 56px 24px; color: var(--fg-2);
}
</style>
