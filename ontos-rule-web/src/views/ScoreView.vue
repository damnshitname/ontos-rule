<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { qualityApi } from '@/api/quality'
import type { ScoreDto, HeatmapDto } from '@/types/domain'
import DimensionRadar from '@/components/DimensionRadar.vue'
import EmptyState from '@/components/EmptyState.vue'

const loading = ref(false)
const project = ref<ScoreDto | null>(null)
const objects = ref<ScoreDto[]>([])
const heatmap = ref<HeatmapDto | null>(null)
const selectedObjId = ref<string>('')

async function load() {
  loading.value = true
  try {
    const [proj, heat] = await Promise.all([
      qualityApi.projectScore(),
      qualityApi.heatmap(),
    ])
    project.value = proj.project
    objects.value = proj.objects ?? []
    heatmap.value = heat
    if (objects.value.length && !selectedObjId.value) {
      selectedObjId.value = objects.value[0].targetId
    }
  } finally { loading.value = false }
  ;(window as any).__refreshMetrics?.()
}

async function recompute() {
  ElMessage.info('正在重算评分...')
  await qualityApi.recompute()
  await load()
  ElMessage.success(`重算完成 · 总分 ${project.value?.overallScore?.toFixed(1) ?? '--'}`)
}

function gradeClass(g?: string): string {
  if (g === 'A+') return 'grade-A-plus'
  if (g === 'A') return 'grade-A'
  if (g === 'B') return 'grade-B'
  if (g === 'C') return 'grade-C'
  return 'grade-D'
}

function barClass(g?: string): string {
  if (g === 'A+' || g === 'A') return 'bar-fill-A'
  if (g === 'B') return 'bar-fill-B'
  if (g === 'C') return 'bar-fill-C'
  return 'bar-fill-D'
}

function heatClass(s: number | null): string {
  if (s == null) return 'heat-low'
  if (s >= 95) return 'heat-95'
  if (s >= 85) return 'heat-85'
  if (s >= 70) return 'heat-70'
  if (s >= 50) return 'heat-50'
  return 'heat-low'
}

function radarColor(g?: string): string {
  switch (g) {
    case 'A+': return '#5fb87a'
    case 'A':  return '#5ba1d4'
    case 'B':  return '#d4a64a'
    case 'C':  return '#d68a4a'
    case 'D':  return '#d97070'
    default:   return '#5a93e6'
  }
}

const selectedObj = computed(() => objects.value.find((o) => o.targetId === selectedObjId.value))

const DIMENSIONS = ['completeness', 'uniqueness', 'validity', 'consistency', 'accuracy', 'timeliness']
const DIM_CN: Record<string, string> = {
  completeness: '完整性',
  uniqueness: '唯一性',
  validity: '有效性',
  consistency: '一致性',
  accuracy: '准确性',
  timeliness: '及时性',
}

function getDimScore(obj: ScoreDto, dim: string): number | null {
  const v = (obj.dimensions as any)?.[dim]
  return v == null ? null : Number(v)
}

function getCellScore(objId: string, dim: string): number | null {
  if (!heatmap.value) return null
  const cell = heatmap.value.cells.find((c) => c.objectId === objId && c.dimension === dim)
  return cell?.score ?? null
}

// 改进建议（找最弱维度）
const weakestDim = computed(() => {
  if (!selectedObj.value) return null
  const scores = DIMENSIONS.map((d) => ({ dim: d, score: getDimScore(selectedObj.value!, d) }))
                          .filter((x) => x.score != null) as { dim: string; score: number }[]
  if (!scores.length) return null
  return scores.reduce((a, b) => (a.score < b.score ? a : b))
})

const strongestDim = computed(() => {
  if (!selectedObj.value) return null
  const scores = DIMENSIONS.map((d) => ({ dim: d, score: getDimScore(selectedObj.value!, d) }))
                          .filter((x) => x.score != null) as { dim: string; score: number }[]
  if (!scores.length) return null
  return scores.reduce((a, b) => (a.score > b.score ? a : b))
})

onMounted(() => {
  load()
  window.addEventListener('ontos-reseed', load)
})
onUnmounted(() => {
  window.removeEventListener('ontos-reseed', load)
})
</script>

<template>
  <div class="score-tab" v-loading="loading">
    <!-- 评分大盘双栏 -->
    <div class="score-grid">

      <!-- ============ 左栏（项目大盘 + 对象排行 + 热力图）============ -->
      <div class="left-col">

        <!-- 项目总分卡 -->
        <div class="panel">
          <div class="panel-header">
            <span class="label">项目质量大盘 · 全局</span>
            <span class="ml-auto label fg-2">DAMA-DMBOK · 6 维度</span>
            <button class="btn btn-xs ml-2" @click="recompute">[ 手动 RUN ]</button>
            <button class="btn btn-xs">[ ⚙ 权重 ]</button>
          </div>
          <div class="p-3 flex items-center gap-4" v-if="project">
            <div class="grade-badge" :class="gradeClass(project.grade)">{{ project.grade }}</div>
            <div style="flex: 1;">
              <div class="flex items-center gap-3">
                <span class="font-mono" style="font-size: 36px; font-weight: 500; line-height: 1;">
                  {{ project.overallScore?.toFixed(2) }}
                </span>
                <span class="label-sm fg-2">总分 / 100</span>
                <span class="tag" :class="`tag-${project.grade === 'A+' || project.grade === 'A' ? 'ok' : project.grade === 'B' ? 'warn' : 'err'}`">
                  {{ project.grade === 'A+' || project.grade === 'A' ? '优秀' : project.grade === 'B' ? '良好' : project.grade === 'C' ? '需改进' : '不合格' }}
                </span>
              </div>
              <div class="label-sm fg-2 mt-2">
                {{ objects.length }} 对象 · {{ project.rulesCount }} 规则 · {{ project.violationsCount }} 违规 / {{ project.totalRows?.toLocaleString() }} 行
              </div>
              <div class="label-sm fg-2 mt-1">
                快照时间: <span class="font-mono fg-1">{{ project.snapshotAt }}</span>
              </div>
            </div>
          </div>
          <EmptyState
            v-else
            icon="◐"
            title="暂无项目评分"
            subtitle="点击 [ 手动 RUN ] 触发重算；或顶栏 [↺ 重置 demo 数据] 加载演示数据"
          />
        </div>

        <!-- 对象排行 -->
        <div class="panel">
          <div class="panel-header">
            <span class="label">对象得分排行</span>
            <span class="ml-auto label-sm fg-2">点击查看维度分布 →</span>
          </div>
          <div style="padding: 8px 0;">
            <div
              v-for="(o, i) in objects" :key="o.targetId"
              class="object-row"
              :class="{ 'object-row-active': o.targetId === selectedObjId }"
              @click="selectedObjId = o.targetId"
            >
              <span class="grade-badge grade-mini" :class="gradeClass(o.grade)">{{ o.grade }}</span>
              <span class="font-mono text-sm" style="width: 100px;">{{ o.targetId }}</span>
              <div class="bar" style="flex: 1;">
                <div class="bar-fill" :class="barClass(o.grade)" :style="{ width: `${o.overallScore ?? 0}%` }"></div>
              </div>
              <span class="font-mono text-sm" style="width: 50px; text-align: right;">{{ o.overallScore?.toFixed(1) }}</span>
              <span class="label-sm fg-3" style="width: 100px; text-align: right;">{{ o.violationsCount }} 违规</span>
              <span v-if="i === 0 && objects.length > 1" class="tag tag-ok">最优</span>
              <span v-else-if="i === objects.length - 1 && objects.length > 1" class="tag tag-err">最差</span>
            </div>
            <div v-if="!loading && objects.length === 0" class="text-center p-3 fg-2">
              暂无对象评分
            </div>
          </div>
        </div>

        <!-- 维度热力图 -->
        <div class="panel" v-if="heatmap && heatmap.objects.length">
          <div class="panel-header">
            <span class="label">维度热力图 · 6 维 × {{ heatmap.objects.length }} 对象</span>
          </div>
          <div style="padding: 8px;">
            <div class="heat-grid" :style="{ gridTemplateColumns: `80px repeat(${heatmap.objects.length}, 1fr)` }">
              <div></div>
              <div v-for="o in heatmap.objects" :key="o" class="label-sm text-center" style="padding: 4px;">{{ o }}</div>

              <template v-for="dim in DIMENSIONS" :key="dim">
                <div class="label-sm" style="padding: 4px 8px; align-self: center;">{{ DIM_CN[dim] }}</div>
                <div
                  v-for="o in heatmap.objects" :key="`${dim}-${o}`"
                  class="heat-cell"
                  :class="heatClass(getCellScore(o, dim))"
                  :title="`${DIM_CN[dim]} · ${o}: ${getCellScore(o, dim) ?? '—'}`"
                >
                  {{ getCellScore(o, dim)?.toFixed(0) ?? '—' }}
                </div>
              </template>
            </div>
            <div class="flex items-center gap-3 mt-3" style="font-size: 10px; padding: 0 8px;">
              <span class="label-sm">图例:</span>
              <span class="heat-cell heat-95" style="width: 32px; display: inline-block; padding: 2px;">95+</span>
              <span class="heat-cell heat-85" style="width: 32px; display: inline-block; padding: 2px;">85+</span>
              <span class="heat-cell heat-70" style="width: 32px; display: inline-block; padding: 2px;">70+</span>
              <span class="heat-cell heat-50" style="width: 32px; display: inline-block; padding: 2px;">50+</span>
              <span class="heat-cell heat-low" style="width: 32px; display: inline-block; padding: 2px;">&lt;50</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ============ 右栏（选中对象详情 + 影响因素）============ -->
      <div class="right-col" v-if="selectedObj">

        <!-- 雷达图 -->
        <div class="panel">
          <div class="panel-header">
            <span class="label">{{ selectedObj.targetId }} · 6 维评分</span>
            <span class="ml-auto label-sm fg-2">{{ selectedObj.rulesCount }} 条规则</span>
          </div>
          <div class="p-3" style="display: flex; flex-direction: column; align-items: center;">
            <div class="grade-badge" :class="gradeClass(selectedObj.grade)" style="width: 96px; height: 96px; font-size: 40px;">
              {{ selectedObj.grade }}
            </div>
            <div class="font-mono mt-2" style="font-size: 32px; font-weight: 500;">
              {{ selectedObj.overallScore?.toFixed(1) }}
            </div>
            <div class="label-sm fg-2">
              总分 · {{ selectedObj.grade === 'A+' || selectedObj.grade === 'A' ? '良好' : selectedObj.grade === 'B' ? '合格' : selectedObj.grade === 'C' ? '待改进' : '不合格' }}
            </div>

            <div class="mt-3" style="width: 100%;">
              <DimensionRadar
                :scores="selectedObj.dimensions || {}"
                :name="selectedObj.targetId"
                :color="radarColor(selectedObj.grade)"
                height="280px"
              />
            </div>

            <div class="flex flex-wrap" style="gap: 6px 12px; margin-top: 8px; justify-content: center;">
              <div v-for="dim in DIMENSIONS" :key="dim" class="flex items-center gap-2" style="font-size: 11px;">
                <span class="dot" :class="(getDimScore(selectedObj, dim) ?? 0) >= 85 ? 'dot-ok' : (getDimScore(selectedObj, dim) ?? 0) >= 70 ? 'dot-warn' : 'dot-err'"></span>
                <span class="fg-1">{{ DIM_CN[dim] }}</span>
                <span class="font-mono fg-0">{{ getDimScore(selectedObj, dim)?.toFixed(0) ?? '—' }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 影响因素 -->
        <div class="panel">
          <div class="panel-header"><span class="label">影响因素 + 改进建议</span></div>
          <div class="p-3" style="font-size: 12px;">
            <div v-if="weakestDim" class="flex items-center gap-2 mb-2">
              <span class="tag tag-err">最弱</span>
              <span class="fg-1">{{ DIM_CN[weakestDim.dim] }} {{ weakestDim.score.toFixed(0) }}</span>
            </div>
            <div v-if="strongestDim" class="flex items-center gap-2 mb-3">
              <span class="tag tag-ok">最强</span>
              <span class="fg-1">{{ DIM_CN[strongestDim.dim] }} {{ strongestDim.score.toFixed(0) }}</span>
            </div>

            <div class="label mb-2">基本指标</div>
            <div class="kv"><span class="kv-k">规则数</span><span class="kv-v">{{ selectedObj.rulesCount }}</span></div>
            <div class="kv"><span class="kv-k">违规数</span><span class="kv-v fg-err">{{ selectedObj.violationsCount }}</span></div>
            <div class="kv"><span class="kv-k">扫描行数</span><span class="kv-v">{{ selectedObj.totalRows?.toLocaleString() }}</span></div>
            <div class="kv"><span class="kv-k">违规率</span><span class="kv-v">{{ selectedObj.totalRows ? ((selectedObj.violationsCount ?? 0) * 100 / selectedObj.totalRows).toFixed(2) + '%' : '—' }}</span></div>

            <div class="label mt-3 mb-2">改进建议</div>
            <ul style="margin: 0; padding-left: 18px; font-size: 11.5px; color: var(--fg-1); line-height: 1.7;">
              <li v-if="weakestDim">
                {{ DIM_CN[weakestDim.dim] }} 维度最弱 ({{ weakestDim.score.toFixed(0) }} 分)，建议补充 2~3 条该维度规则
              </li>
              <li v-if="(selectedObj.violationsCount ?? 0) > 0">
                当前 {{ selectedObj.violationsCount }} 条违规，建议联动 Action 引擎自动处置
              </li>
              <li>
                可通过 PUT /api/quality/weights 调整该对象的维度权重
              </li>
            </ul>
          </div>
        </div>

      </div>
    </div>

    <!-- 底部说明 -->
    <div class="callout" style="margin: 12px;">
      <span class="label fg-info">关于评分</span>
      <span class="ml-2">
        6 维度参考 <strong style="color: var(--fg-0)">DAMA-DMBOK 数据质量框架</strong>。
        算法: 单规则通过率 → 维度算术平均 → 对象加权平均 → 项目总分。
        等级 5 档: A+(95) / A(85) / B(70) / C(50) / D 。
        权重可配 · 每日自动 cron 重算 · 30 天历史快照。
      </span>
    </div>
  </div>
</template>

<style scoped>
.score-grid {
  display: grid; grid-template-columns: 1.6fr 1fr;
  gap: 12px; margin: 12px;
}
.left-col, .right-col {
  display: flex; flex-direction: column; gap: 12px;
}
.object-row {
  padding: 8px 14px; cursor: pointer;
  border-left: 2px solid transparent;
  display: flex; align-items: center; gap: 8px;
}
.object-row:hover { background: var(--bg-2); }
.object-row-active {
  background: var(--bg-2);
  border-left-color: var(--accent);
}
.heat-grid {
  display: grid; gap: 1px;
}
.flex-wrap { flex-wrap: wrap; }
</style>
