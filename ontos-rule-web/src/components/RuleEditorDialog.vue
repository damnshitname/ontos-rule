<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { rulesApi, type RulePreviewResponse } from '@/api/rules'
import type { RuleDto, CheckSpec } from '@/types/domain'

interface Props {
  modelValue: boolean
  /** 传入则为编辑，不传则为新建 */
  editing?: RuleDto | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  saved: [r: RuleDto]
}>()

const mode = ref<'FORM' | 'CEL'>('FORM')

const form = ref({
  id: '',
  name: '',
  target: '',
  severity: 'ERROR' as 'ERROR' | 'WARN',
  backendHint: 'AUTO' as 'AUTO' | 'JVM' | 'SQL' | 'SPARK',
  owner: '',
  expression: '',
  dimensions: '',
})

interface Card extends CheckSpec {
  checked: boolean
}

const cardDefs: Array<{ type: string; label: string; dimension: string; paramsSchema?: any[] }> = [
  { type: 'not_null', label: '空值校验', dimension: 'completeness' },
  { type: 'not_blank', label: '非空字符串', dimension: 'completeness' },
  { type: 'unique', label: '唯一性', dimension: 'uniqueness' },
  { type: 'pattern', label: '正则匹配', dimension: 'validity', paramsSchema: [{ key: 'regex', label: '正则', placeholder: '^LOT-' }] },
  { type: 'range', label: '数值范围', dimension: 'validity', paramsSchema: [{ key: 'min', label: '最小' }, { key: 'max', label: '最大' }] },
  { type: 'length', label: '长度', dimension: 'validity', paramsSchema: [{ key: 'min', label: '最小' }, { key: 'max', label: '最大' }] },
  { type: 'enum', label: '枚举值', dimension: 'validity', paramsSchema: [{ key: 'allowed', label: '枚举(逗号分隔)', placeholder: 'A,B,C' }] },
  { type: 'starts_with', label: '字符串前缀', dimension: 'validity', paramsSchema: [{ key: 'prefix', label: '前缀' }] },
  { type: 'time_within', label: '时间窗口', dimension: 'timeliness', paramsSchema: [{ key: 'days', label: '天数' }] },
  { type: 'cross_field', label: '跨字段比较', dimension: 'consistency', paramsSchema: [{ key: 'left', label: '左字段' }, { key: 'op', label: '运算符', placeholder: '== / != / > / < ...' }, { key: 'right', label: '右字段' }] },
]

const cards = ref<Card[]>(
  cardDefs.map((c) => ({ type: c.type, params: {}, checked: false }))
)

const inferredDims = computed(() => {
  const set = new Set<string>()
  cards.value.forEach((c, idx) => {
    if (c.checked) set.add(cardDefs[idx].dimension)
  })
  return Array.from(set)
})

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const isEditing = computed(() => !!props.editing)

watch(() => props.modelValue, (v) => {
  if (v) reset()
})

function reset() {
  if (props.editing) {
    const r = props.editing
    mode.value = r.mode || 'CEL'
    form.value = {
      id: r.id,
      name: r.name,
      target: r.target,
      severity: r.severity || 'ERROR',
      backendHint: r.backendHint || 'AUTO',
      owner: r.owner || '',
      expression: r.expression || '',
      dimensions: r.dimensions || '',
    }
    cards.value = cardDefs.map((c) => ({ type: c.type, params: {}, checked: false }))
    if (r.formChecks) {
      r.formChecks.forEach((spec) => {
        const idx = cardDefs.findIndex((c) => c.type === spec.type)
        if (idx >= 0) {
          cards.value[idx].checked = true
          cards.value[idx].params = { ...(spec.params || {}) }
        }
      })
    }
  } else {
    mode.value = 'FORM'
    form.value = { id: '', name: '', target: '', severity: 'ERROR', backendHint: 'AUTO', owner: 'demo', expression: '', dimensions: '' }
    cards.value = cardDefs.map((c) => ({ type: c.type, params: {}, checked: false }))
  }
}

const saving = ref(false)

async function submit() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  if (!form.value.target.trim()) {
    ElMessage.warning('请填写 Target，如 Lot.lotId')
    return
  }

  saving.value = true
  try {
    const checked = cards.value
      .filter((c) => c.checked)
      .map(({ type, params }) => ({ type, params }))

    if (mode.value === 'FORM' && checked.length === 0) {
      ElMessage.warning('维度卡片形式至少选择一个')
      saving.value = false
      return
    }
    if (mode.value === 'CEL' && !form.value.expression.trim()) {
      ElMessage.warning('CEL 形式需要填写表达式')
      saving.value = false
      return
    }

    const body: any = {
      name: form.value.name,
      target: form.value.target,
      severity: form.value.severity,
      backendHint: form.value.backendHint,
      owner: form.value.owner || 'demo',
    }
    if (form.value.id && !isEditing.value) body.id = form.value.id
    if (mode.value === 'FORM') {
      body.formChecks = checked
    } else {
      body.expression = form.value.expression
      if (form.value.dimensions) body.dimensions = form.value.dimensions
    }

    const saved = isEditing.value
      ? await rulesApi.update(form.value.id, body)
      : await rulesApi.create(body)
    ElMessage.success(isEditing.value ? '更新成功' : '创建成功')
    emit('saved', saved)
    dialogVisible.value = false
  } catch (e: any) {
    // axios 拦截已经弹了 toast
  } finally {
    saving.value = false
  }
}

function close() { dialogVisible.value = false }

// ============= 实时预览 =============
const preview = ref<RulePreviewResponse | null>(null)
const previewLoading = ref(false)
let previewTimer: number | null = null

function schedulePreview() {
  if (previewTimer) clearTimeout(previewTimer)
  previewTimer = window.setTimeout(runPreview, 300)
}

async function runPreview() {
  const hasForm = mode.value === 'FORM' && cards.value.some((c) => c.checked)
  const hasCel = mode.value === 'CEL' && form.value.expression.trim().length > 0
  if (!hasForm && !hasCel) { preview.value = null; return }

  previewLoading.value = true
  try {
    const body: any = {
      target: form.value.target || '',
      dimensions: form.value.dimensions || null,
    }
    if (mode.value === 'FORM') {
      body.formChecks = cards.value
        .filter((c) => c.checked)
        .map(({ type, params }) => ({ type, params }))
    } else {
      body.expression = form.value.expression
    }
    preview.value = await rulesApi.preview(body)
  } catch {
    preview.value = null
  } finally {
    previewLoading.value = false
  }
}

// 监听变化触发预览（任何相关字段改了都 debounce 300ms）
watch(
  () => [mode.value, form.value.expression, form.value.target, form.value.dimensions,
         cards.value.map((c) => `${c.checked}:${JSON.stringify(c.params)}`).join('|')],
  schedulePreview,
  { deep: true }
)

// 打开 dialog 时立即跑一次
watch(() => props.modelValue, (v) => { if (v) setTimeout(runPreview, 100); else preview.value = null })
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    width="980px"
    top="6vh"
    :show-close="false"
    :close-on-click-modal="false"
    destroy-on-close
    class="rule-editor-dialog"
  >
    <!-- 自定义 header（panel-header 风格） -->
    <template #header>
      <div class="panel-header">
        <span class="label">{{ isEditing ? '编辑规则' : '新建规则' }}</span>
        <span v-if="isEditing" class="font-mono ml-2">{{ editing?.id }}</span>
        <span v-if="isEditing && editing?.name" class="fg-2 ml-2">· {{ editing?.name }}</span>
        <span class="tag tag-warn ml-2">QUALITY</span>
        <span class="close-x" @click="close">&times;</span>
      </div>
    </template>

    <!-- 基本信息：两行，每行三列 -->
    <div class="info-grid">
      <div class="field">
        <div class="label-sm mb-2">规则 ID</div>
        <input class="input input-mono" v-model="form.id" :disabled="isEditing"
               placeholder="留空自动生成 QR-XXXX" />
      </div>
      <div class="field">
        <div class="label-sm mb-2">名称 *</div>
        <input class="input" v-model="form.name" placeholder="如：Lot.lotId 必填且符合命名" />
      </div>
      <div class="field">
        <div class="label-sm mb-2">Target *</div>
        <input class="input input-mono" v-model="form.target" placeholder="Lot.lotId" />
      </div>
      <div class="field">
        <div class="label-sm mb-2">严重度</div>
        <select class="select" v-model="form.severity">
          <option value="ERROR">ERROR</option>
          <option value="WARN">WARN</option>
        </select>
      </div>
      <div class="field">
        <div class="label-sm mb-2">BACKEND</div>
        <select class="select" v-model="form.backendHint">
          <option value="AUTO">AUTO</option>
          <option value="JVM">JVM</option>
          <option value="SQL">SQL</option>
        </select>
      </div>
      <div class="field">
        <div class="label-sm mb-2">负责人</div>
        <input class="input" v-model="form.owner" placeholder="张工" />
      </div>
    </div>

    <!-- 左右分栏：维度卡片 | CEL 表达式 -->
    <div class="split-pane">
      <!-- 左：维度卡片 -->
      <div class="pane" :class="{ active: mode === 'FORM' }" @click="mode = 'FORM'">
        <div class="pane-head">
          <span class="label">维度卡片</span>
          <span class="label-sm fg-3 ml-auto">勾选即用 · 自动编译为 CEL</span>
        </div>
        <div class="pane-body">
          <div class="cards-grid">
            <div
              v-for="(c, i) in cards" :key="c.type"
              class="card"
              :class="{ checked: c.checked }"
              @click.stop="mode = 'FORM'; c.checked = !c.checked"
            >
              <div class="card-head">
                <span class="check-box" :class="{ on: c.checked }"></span>
                <span class="card-type">{{ c.type }}</span>
              </div>
              <div class="card-label">{{ cardDefs[i].label }}</div>
              <div class="card-dim font-mono">→ {{ cardDefs[i].dimension }}</div>
              <div v-if="c.checked && cardDefs[i].paramsSchema" class="card-params" @click.stop>
                <div v-for="p in cardDefs[i].paramsSchema" :key="p.key" class="param-row">
                  <span class="param-key">{{ p.label }}</span>
                  <input
                    class="input input-mono param-input"
                    v-model="(c.params as any)[p.key]"
                    :placeholder="p.placeholder || p.label"
                    @click.stop
                  />
                </div>
              </div>
            </div>
          </div>
          <div v-if="inferredDims.length" class="inferred-tip">
            <span class="label-sm">推断维度</span>
            <span v-for="d in inferredDims" :key="d" class="tag tag-info">{{ d }}</span>
          </div>
        </div>
      </div>

      <!-- 右：CEL 表达式 -->
      <div class="pane" :class="{ active: mode === 'CEL' }" @click="mode = 'CEL'">
        <div class="pane-head">
          <span class="label">CEL 表达式</span>
          <span class="label-sm fg-3 ml-auto">自由表达式</span>
        </div>
        <div class="pane-body cel-body">
          <textarea
            class="cel-textarea"
            v-model="form.expression"
            placeholder="如：temperature >= 100 && temperature <= 800"
            @focus="mode = 'CEL'"
          ></textarea>
          <div class="cel-foot">
            <span class="label-sm">维度</span>
            <input
              class="input input-mono"
              v-model="form.dimensions"
              placeholder="逗号分隔，如 validity,consistency；留空默认 validity"
              @focus="mode = 'CEL'"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 实时预览块 -->
    <div class="preview-block">
      <div class="preview-head">
        <span class="label">实时预览</span>
        <span class="label-sm fg-3">debounce 300ms · 后端编译</span>
        <span v-if="previewLoading" class="ml-auto label-sm fg-info">编译中…</span>
        <span v-else-if="preview?.valid" class="ml-auto label-sm fg-ok">✓ 语法正确</span>
        <span v-else-if="preview" class="ml-auto label-sm fg-err">✗ 编译失败</span>
        <span v-else class="ml-auto label-sm fg-3">等待输入</span>
      </div>
      <div class="preview-body" v-if="preview">
        <div class="pv-row">
          <span class="label-sm pv-key">编译产物</span>
          <code class="pv-code" :class="{ 'pv-code-err': !preview.valid }">{{ preview.compiledExpression || '(空)' }}</code>
        </div>
        <div class="pv-row">
          <span class="label-sm pv-key">检测变量</span>
          <div class="pv-tags">
            <span v-for="v in preview.variables" :key="v" class="tag tag-info">{{ v }}</span>
            <span v-if="!preview.variables?.length" class="fg-3 font-mono" style="font-size: 11px;">—</span>
          </div>
        </div>
        <div class="pv-row" v-if="preview.inferredDimensions?.length">
          <span class="label-sm pv-key">推断维度</span>
          <div class="pv-tags">
            <span v-for="d in preview.inferredDimensions" :key="d" class="tag tag-warn">{{ d }}</span>
          </div>
        </div>
        <div v-if="!preview.valid && preview.errorMessage" class="pv-error">
          <span class="tag tag-err">ERR</span>
          <span class="font-mono">{{ preview.errorMessage }}</span>
        </div>
      </div>
      <div v-else class="preview-empty">
        填写表达式或勾选维度卡片，预览会自动出现
      </div>
    </div>

    <!-- 自定义 footer -->
    <template #footer>
      <div class="footer-bar">
        <span class="label-sm">保存为</span>
        <div class="form-cel-toggle">
          <button :class="{ active: mode === 'FORM' }" @click="mode = 'FORM'">维度卡片</button>
          <button :class="{ active: mode === 'CEL' }" @click="mode = 'CEL'">CEL</button>
        </div>
        <span style="flex: 1"></span>
        <button class="btn btn-sm" @click="close">取消</button>
        <button class="btn btn-pri btn-sm" :disabled="saving" @click="submit">
          {{ saving ? '保存中…' : (isEditing ? '[ 保存 ]' : '[ 创建 ]') }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
/* 基本信息两行三列 */
.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px 12px;
  padding: 10px 12px;
  background: var(--bg-2);
  border: 1px solid var(--bg-4);
}
.field { min-width: 0; }
.input, .select {
  background: var(--bg-0); border: 1px solid var(--bg-4);
  color: var(--fg-0); padding: 4px 8px;
  font-size: 12px; font-family: var(--font-sans);
  width: 100%; outline: none; height: 26px; line-height: 1.4;
}
.input:focus, .select:focus { border-color: var(--accent); }
.input:disabled { color: var(--fg-3); background: var(--bg-1); }
.input-mono { font-family: var(--font-mono); }

/* 左右分栏 */
.split-pane {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 1px;
  background: var(--bg-4);
  border: 1px solid var(--bg-4);
  border-top: none;
  height: 440px;
}
.pane {
  background: var(--bg-1);
  display: flex; flex-direction: column;
  cursor: pointer;
  overflow: hidden;
}
.pane:not(.active) { opacity: 0.72; }
.pane:hover { opacity: 1; }
.pane-head {
  display: flex; align-items: center; gap: 8px;
  background: var(--bg-2); border-bottom: 1px solid var(--bg-4);
  padding: 6px 12px; min-height: 28px;
}
.pane.active .pane-head .label { color: var(--accent); }
.pane-body {
  flex: 1; overflow: auto; padding: 10px 12px;
}

/* 维度卡片 */
.cards-grid {
  display: grid; grid-template-columns: repeat(2, 1fr); gap: 6px;
}
.card {
  border: 1px solid var(--bg-3); background: var(--bg-2);
  padding: 7px 9px; cursor: pointer;
  transition: all 0.12s;
}
.card:hover { border-color: var(--accent); }
.card.checked { border-color: var(--accent); background: var(--accent-dim); }
.card-head { display: flex; align-items: center; gap: 6px; }
.check-box {
  width: 11px; height: 11px; border: 1px solid var(--fg-3);
  background: var(--bg-0); flex-shrink: 0;
  position: relative;
}
.check-box.on { background: var(--accent); border-color: var(--accent); }
.check-box.on::after {
  content: ''; position: absolute; left: 2px; top: -1px;
  width: 4px; height: 8px;
  border: solid #fff; border-width: 0 1.5px 1.5px 0;
  transform: rotate(45deg);
}
.card-type {
  font-family: var(--font-mono); font-size: 10px;
  color: var(--accent); font-weight: 600; letter-spacing: 0.02em;
}
.card-label { margin-top: 3px; font-size: 11.5px; color: var(--fg-0); }
.card-dim { margin-top: 1px; color: var(--fg-2); font-size: 10px; }
.card-params { margin-top: 6px; display: flex; flex-direction: column; gap: 3px; }
.param-row { display: flex; align-items: center; gap: 4px; }
.param-key {
  font-family: var(--font-mono); font-size: 10px; color: var(--fg-2);
  flex-shrink: 0; min-width: 38px;
}
.param-input { height: 22px; padding: 2px 6px; font-size: 11px; }

.inferred-tip {
  margin-top: 10px; padding: 6px 10px;
  background: var(--bg-2); border: 1px dashed var(--bg-3);
  display: flex; gap: 6px; align-items: center; flex-wrap: wrap;
}

/* CEL */
.cel-body { display: flex; flex-direction: column; gap: 8px; padding: 10px 12px; }
.cel-textarea {
  flex: 1;
  background: var(--bg-0); border: 1px solid var(--bg-3);
  color: var(--fg-0); font-family: var(--font-mono);
  font-size: 12px; line-height: 1.7; padding: 10px 12px;
  resize: none; outline: none; min-height: 220px;
}
.cel-textarea:focus { border-color: var(--accent); }
.cel-foot { display: flex; align-items: center; gap: 8px; }
.cel-foot .input { flex: 1; }

/* 关闭按钮 */
.close-x {
  margin-left: auto;
  font-size: 18px; cursor: pointer; color: var(--fg-2); line-height: 1;
  padding: 2px 4px;
}
.close-x:hover { color: var(--fg-0); }

/* footer */
.footer-bar { display: flex; align-items: center; gap: 8px; }

/* 实时预览块 */
.preview-block {
  border: 1px solid var(--bg-4);
  border-top: none;
  background: var(--bg-1);
}
.preview-head {
  display: flex; align-items: center; gap: 8px;
  background: var(--bg-2);
  border-bottom: 1px solid var(--bg-4);
  padding: 6px 12px;
  min-height: 28px;
}
.preview-body {
  padding: 10px 12px;
  display: flex; flex-direction: column; gap: 8px;
}
.preview-empty {
  padding: 14px 12px; text-align: center;
  color: var(--fg-3); font-family: var(--font-mono); font-size: 11px;
}
.pv-row {
  display: flex; align-items: flex-start; gap: 10px;
}
.pv-key {
  flex-shrink: 0; width: 64px;
  padding-top: 3px;
}
.pv-code {
  flex: 1; display: block;
  background: var(--bg-0); border: 1px solid var(--bg-3);
  padding: 6px 10px;
  font-family: var(--font-mono); font-size: 11.5px;
  color: var(--fg-1);
  word-break: break-all; line-height: 1.6;
}
.pv-code-err { color: var(--err); }
.pv-tags {
  display: flex; flex-wrap: wrap; gap: 4px; flex: 1;
  padding-top: 2px;
}
.pv-tags .tag {
  display: inline-flex; align-items: center;
  padding: 1px 6px;
  font-family: var(--font-mono); font-size: 10px;
  border: 1px solid; text-transform: uppercase; letter-spacing: 0.04em;
}
.pv-tags .tag-info { color: var(--info); border-color: var(--info); }
.pv-tags .tag-warn { color: var(--warn); border-color: var(--warn); }
.pv-error {
  display: flex; gap: 6px; align-items: flex-start;
  padding: 6px 10px;
  background: var(--err-dim); border-left: 2px solid var(--err);
  font-size: 11px; color: var(--fg-0);
}
.pv-error .tag-err {
  flex-shrink: 0; color: var(--err); border: 1px solid var(--err);
  padding: 1px 6px; font-family: var(--font-mono); font-size: 10px;
}

.mb-2 { margin-bottom: 4px; }
.ml-2 { margin-left: 8px; }
.ml-auto { margin-left: auto; }
.font-mono { font-family: var(--font-mono); font-size: 11px; }
.fg-2 { color: var(--fg-2); }
.fg-3 { color: var(--fg-3); }
</style>

<style>
/* 全局穿透：让 el-dialog 完全贴合原型 */
.rule-editor-dialog {
  --el-dialog-padding-primary: 0;
}
.rule-editor-dialog .el-dialog__header {
  padding: 0;
  margin: 0;
  border-bottom: 1px solid var(--bg-4);
}
.rule-editor-dialog .el-dialog__header .panel-header {
  margin: 0;
  padding: 8px 14px;
  min-height: 36px;
}
.rule-editor-dialog .el-dialog__body {
  padding: 0;
  color: var(--fg-0);
  font-size: 12px;
}
.rule-editor-dialog .el-dialog__footer {
  padding: 8px 12px;
  background: var(--bg-2);
  border-top: 1px solid var(--bg-4);
}
.rule-editor-dialog .el-dialog {
  background: var(--bg-1);
  border: 1px solid var(--bg-5);
}
</style>
