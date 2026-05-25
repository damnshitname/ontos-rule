<script setup lang="ts">
import { ref } from 'vue'
import type { RuleRunDto } from '@/types/domain'

interface RunningState {
  ruleId: string
  ruleName: string
  dataSource: string
  startedAt: number
  progress: number
  done: boolean
  result?: RuleRunDto
  error?: string
}

const visible = ref(false)
const state = ref<RunningState | null>(null)
let progressTimer: number | null = null
let dismissTimer: number | null = null

function reset() {
  if (progressTimer) { clearInterval(progressTimer); progressTimer = null }
  if (dismissTimer) { clearTimeout(dismissTimer); dismissTimer = null }
}

function start(ruleId: string, ruleName: string, dataSource: string) {
  reset()
  visible.value = true
  state.value = {
    ruleId, ruleName, dataSource,
    startedAt: Date.now(),
    progress: 0,
    done: false,
  }
  // 假进度条：300ms 涨到 80%，之后慢爬等待真结果
  progressTimer = window.setInterval(() => {
    if (!state.value || state.value.done) return
    const target = state.value.progress < 80 ? 80 : 95
    state.value.progress = Math.min(target, state.value.progress + (target - state.value.progress) * 0.15)
  }, 50)
}

function done(result: RuleRunDto) {
  if (!state.value) return
  state.value.done = true
  state.value.progress = 100
  state.value.result = result
  reset()
  // 6 秒后自动消失
  dismissTimer = window.setTimeout(() => { visible.value = false }, 6000)
}

function fail(message: string) {
  if (!state.value) return
  state.value.done = true
  state.value.progress = 100
  state.value.error = message
  reset()
  dismissTimer = window.setTimeout(() => { visible.value = false }, 6000)
}

function close() {
  visible.value = false
  reset()
}

defineExpose({ start, done, fail, close })

function violationRate(r?: RuleRunDto): string {
  if (!r || !r.totalRows) return '—'
  return (((r.violationCount ?? 0) * 100) / r.totalRows).toFixed(2) + '%'
}

function fmtMs(ms?: number): string {
  if (ms == null) return '—'
  if (ms < 1000) return ms + ' ms'
  return (ms / 1000).toFixed(2) + ' s'
}
</script>

<template>
  <Teleport to="body">
    <Transition name="slide-in">
      <div v-if="visible && state" class="run-toast">
        <div class="rt-head">
          <span class="dot" :class="state.error ? 'dot-err' : state.done ? 'dot-ok' : 'dot-info dot-live'"></span>
          <span class="label">{{ state.error ? '执行失败' : state.done ? '执行完成' : '执行中' }}</span>
          <span class="font-mono fg-2 ml-2" style="font-size: 11px;">{{ state.ruleId }}</span>
          <span class="close-x" @click="close">×</span>
        </div>
        <div class="rt-body">
          <div class="rt-name">{{ state.ruleName }}</div>
          <div class="rt-meta">
            <span class="label-sm">数据源</span>
            <span class="font-mono">{{ state.dataSource }}</span>
          </div>

          <!-- 进度条 -->
          <div class="bar rt-bar">
            <div
              class="bar-fill"
              :class="state.error ? 'bar-fill-D' : 'bar-fill-A'"
              :style="{ width: state.progress + '%' }"
            ></div>
          </div>

          <!-- 结果 -->
          <div v-if="state.done && state.result" class="rt-result">
            <div class="rt-kpi">
              <div class="kpi">
                <div class="kpi-l">违规 / 总数</div>
                <div class="kpi-v">
                  <span :class="(state.result.violationCount ?? 0) > 0 ? 'fg-warn' : 'fg-ok'">
                    {{ state.result.violationCount }}
                  </span>
                  <span class="fg-3"> / {{ state.result.totalRows?.toLocaleString() }}</span>
                </div>
              </div>
              <div class="kpi">
                <div class="kpi-l">违规率</div>
                <div class="kpi-v" :class="(state.result.violationCount ?? 0) > 0 ? 'fg-warn' : 'fg-ok'">
                  {{ violationRate(state.result) }}
                </div>
              </div>
              <div class="kpi">
                <div class="kpi-l">耗时</div>
                <div class="kpi-v fg-info">{{ fmtMs(state.result.durationMs) }}</div>
              </div>
              <div class="kpi">
                <div class="kpi-l">BACKEND</div>
                <div class="kpi-v">
                  <span class="tag tag-info">{{ state.result.backendUsed }}</span>
                </div>
              </div>
            </div>
          </div>

          <div v-else-if="state.error" class="rt-error">
            <span class="tag tag-err">ERR</span>
            <span class="ml-2">{{ state.error }}</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.run-toast {
  position: fixed;
  top: 60px; right: 16px;
  width: 380px;
  background: var(--bg-1);
  border: 1px solid var(--bg-5);
  z-index: 9999;
  box-shadow: 0 4px 24px rgba(0,0,0,0.4);
  font-family: var(--font-sans);
  font-size: 12px;
}
.rt-head {
  display: flex; align-items: center; gap: 6px;
  background: var(--bg-2);
  border-bottom: 1px solid var(--bg-4);
  padding: 6px 12px;
}
.close-x {
  margin-left: auto;
  cursor: pointer;
  color: var(--fg-3);
  font-size: 16px; line-height: 1;
  padding: 0 4px;
}
.close-x:hover { color: var(--fg-0); }

.rt-body { padding: 10px 12px; }
.rt-name { font-size: 13px; color: var(--fg-0); margin-bottom: 6px; }
.rt-meta { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
.rt-meta .label-sm { color: var(--fg-2); }
.rt-meta .font-mono { color: var(--fg-1); font-size: 11px; }

.rt-bar { height: 6px; background: var(--bg-3); margin-bottom: 10px; }
.bar-fill { height: 100%; transition: width 0.2s; }

.rt-kpi {
  display: grid; grid-template-columns: 1fr 1fr;
  gap: 8px 12px;
  padding-top: 8px;
  border-top: 1px dashed var(--bg-3);
}
.kpi { display: flex; flex-direction: column; gap: 2px; }
.kpi-l {
  font-family: var(--font-mono);
  font-size: 9px; letter-spacing: 0.1em;
  color: var(--fg-3); text-transform: uppercase;
}
.kpi-v {
  font-family: var(--font-mono);
  font-size: 14px; color: var(--fg-0);
  font-weight: 500;
}

.rt-error { padding: 6px 0; color: var(--fg-1); }

.label { font-family: var(--font-mono); font-size: 10px; letter-spacing: 0.08em; color: var(--fg-2); text-transform: uppercase; }
.label-sm { font-family: var(--font-mono); font-size: 10px; color: var(--fg-2); text-transform: uppercase; }
.ml-2 { margin-left: 8px; }
.font-mono { font-family: var(--font-mono); }
.fg-0 { color: var(--fg-0); } .fg-1 { color: var(--fg-1); } .fg-2 { color: var(--fg-2); } .fg-3 { color: var(--fg-3); }
.fg-ok { color: var(--ok); } .fg-warn { color: var(--warn); } .fg-info { color: var(--info); }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dot-ok { background: var(--ok); }
.dot-err { background: var(--err); }
.dot-info { background: var(--info); }
@keyframes pulseDot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.3); }
}
.dot-live { animation: pulseDot 1s infinite; }

.tag {
  display: inline-flex; align-items: center;
  padding: 0 6px;
  font-family: var(--font-mono); font-size: 10px;
  border: 1px solid; text-transform: uppercase; letter-spacing: 0.04em;
}
.tag-info { color: var(--info); border-color: var(--info); }
.tag-err { color: var(--err); border-color: var(--err); }
.bar-fill-A { background: var(--ok); }
.bar-fill-D { background: var(--err); }
.bar { display: block; }

/* 入场动画 */
.slide-in-enter-active, .slide-in-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.slide-in-enter-from {
  transform: translateX(110%); opacity: 0;
}
.slide-in-leave-to {
  transform: translateX(110%); opacity: 0;
}
</style>
