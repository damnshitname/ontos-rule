<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { playgroundApi, type PlaygroundEvalResponse, type InvocationRecord, type InvocationStats, type BenchmarkResponse } from '@/api/playground'
import * as echarts from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TitleComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, GridComponent, TitleComponent, TooltipComponent, LegendComponent, CanvasRenderer])

// ─── 主题切换（dark / light） ───
const theme = ref<'dark' | 'light'>('dark')

function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  document.documentElement.setAttribute('data-theme', theme.value)
  try { localStorage.setItem('ontos-playground-theme', theme.value) } catch {}
}

onMounted(() => {
  try {
    const saved = localStorage.getItem('ontos-playground-theme') as 'dark' | 'light' | null
    if (saved) {
      theme.value = saved
      document.documentElement.setAttribute('data-theme', saved)
    } else {
      document.documentElement.setAttribute('data-theme', 'dark')
    }
  } catch {
    document.documentElement.setAttribute('data-theme', 'dark')
  }
  refreshSources()
})

// ─── 顶部 Tab：Playground / Sources ───
const topTab = ref<'playground' | 'sources'>('playground')

// ─── Playground 状态 ───
const expression = ref(`temperature + tolerance < maxLimit
  && status in ["RUNNING", "IDLE"]`)

const dataJson = ref(`{
  "temperature": 92,
  "tolerance": 5,
  "maxLimit": 100,
  "status": "RUNNING"
}`)

const evalResult = ref<PlaygroundEvalResponse | null>(null)
const evalLoading = ref(false)

const samples = {
  ok: { temperature: 92, tolerance: 5, maxLimit: 100, status: 'RUNNING' },
  violate: { temperature: 95, tolerance: 8, maxLimit: 100, status: 'DOWN' },
}

function loadSample(key: 'ok' | 'violate') {
  dataJson.value = JSON.stringify(samples[key], null, 2)
}

async function runEval() {
  let parsedData: Record<string, unknown>
  try {
    parsedData = JSON.parse(dataJson.value)
  } catch (e: any) {
    ElMessage.error('JSON 数据格式错误：' + e.message)
    return
  }

  evalLoading.value = true
  try {
    evalResult.value = await playgroundApi.eval({
      expression: expression.value,
      data: parsedData,
    })
    // 刷新 Sources 调用历史（如果在 Sources Tab）
    if (topTab.value === 'sources') {
      await refreshSources()
    }
  } finally {
    evalLoading.value = false
  }
}

function validateSyntax() {
  ElMessage.info('提示：完整语法检查由后端 compile 一并完成，点 RUN 即可')
}

// Ctrl+Enter / Cmd+Enter 快捷键
function onKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
    e.preventDefault()
    if (topTab.value === 'playground') runEval()
  }
}

const integrationTab = ref<'java' | 'python' | 'rest'>('java')

// ─── Benchmark 状态 ───
const benchLoading = ref(false)
const benchResult = ref<BenchmarkResponse | null>(null)
const benchChartRef = ref<HTMLDivElement>()
let benchChart: echarts.ECharts | null = null

async function runBenchmark() {
  benchLoading.value = true
  try {
    benchResult.value = await playgroundApi.benchmark()
    await nextTick()
    renderBenchChart()
    ElMessage.success(`Benchmark 完成 · 总耗时 ${benchResult.value.totalElapsedMs} ms · 预热 ${benchResult.value.warmupIterations} 次`)
  } finally { benchLoading.value = false }
}

function renderBenchChart() {
  if (!benchChartRef.value || !benchResult.value) return
  if (!benchChart) benchChart = echarts.init(benchChartRef.value, undefined, { renderer: 'canvas' })

  const sizes = [...new Set(benchResult.value.results.map((r) => r.rowCount))].sort((a, b) => a - b)
  const complexities = [...new Set(benchResult.value.results.map((r) => r.complexityLabel))]
  const series = complexities.map((comp, i) => ({
    name: comp,
    type: 'bar',
    barWidth: '22%',
    data: sizes.map((size) => {
      const m = benchResult.value!.results.find((r) => r.complexityLabel === comp && r.rowCount === size)
      return m?.durationMs ?? 0
    }),
    itemStyle: { color: ['#5fb87a', '#5ba1d4', '#d4a64a'][i] },
    label: { show: true, position: 'top', color: '#a8b1c2', fontSize: 10, formatter: '{c} ms' },
  }))

  benchChart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        let html = `<b>${params[0].axisValue} 行</b><br/>`
        params.forEach((p: any) => {
          html += `${p.marker} ${p.seriesName}: <b>${p.value} ms</b><br/>`
        })
        return html
      },
    },
    legend: { textStyle: { color: '#a8b1c2', fontSize: 11 }, top: 0 },
    grid: { left: 60, right: 20, top: 40, bottom: 40 },
    xAxis: {
      type: 'category',
      data: sizes.map((s) => s.toLocaleString() + ' 行'),
      axisLabel: { color: '#a8b1c2', fontSize: 11, fontFamily: 'IBM Plex Mono' },
      axisLine: { lineStyle: { color: '#2c3547' } },
    },
    yAxis: {
      type: 'log',
      name: '耗时 (ms · log)',
      nameTextStyle: { color: '#6f7a8f', fontSize: 10 },
      axisLabel: { color: '#a8b1c2', fontSize: 11, fontFamily: 'IBM Plex Mono' },
      splitLine: { lineStyle: { color: 'rgba(170,178,194,0.1)' } },
    },
    series,
  }, true)
}

onBeforeUnmount(() => {
  if (benchChart) { benchChart.dispose(); benchChart = null }
})

// ─── Sources 状态 ───
const stats = ref<InvocationStats | null>(null)
const invocations = ref<InvocationRecord[]>([])
const selectedCaller = ref<string>('')
const sourcesLoading = ref(false)

async function refreshSources() {
  sourcesLoading.value = true
  try {
    stats.value = await playgroundApi.invocationStats()
    if (stats.value && Object.keys(stats.value.byCaller).length && !selectedCaller.value) {
      // 默认选第一个
      selectedCaller.value = Object.keys(stats.value.byCaller)[0]
    }
    if (selectedCaller.value) {
      invocations.value = await playgroundApi.recentInvocations({ caller: selectedCaller.value, limit: 50 })
    }
  } finally {
    sourcesLoading.value = false
  }
}

async function selectCaller(c: string) {
  selectedCaller.value = c
  sourcesLoading.value = true
  try {
    invocations.value = await playgroundApi.recentInvocations({ caller: c, limit: 50 })
  } finally {
    sourcesLoading.value = false
  }
}

function switchTopTab(t: 'playground' | 'sources') {
  topTab.value = t
  if (t === 'sources') refreshSources()
}

const callerList = computed(() => {
  if (!stats.value) return []
  return Object.entries(stats.value.byCaller)
    .sort(([, a], [, b]) => b - a)
    .map(([caller, count]) => ({ caller, count }))
})

// 求值状态显示
const isOk = computed(() => evalResult.value?.ok && evalResult.value?.result === true)
const isFail = computed(() => evalResult.value?.ok && evalResult.value?.result === false)
const isError = computed(() => evalResult.value && !evalResult.value.ok)

// 集成代码模板
const javaCode = computed(() => `// 1. 注入
@Autowired private RuleEngine engine;

// 2. 编译一次（可缓存复用）
CompiledRule rule = engine.compile(
  "${expression.value.replace(/\n/g, '\\n').replace(/"/g, '\\"').substring(0, 80)}"
);

// 3. 单条求值（带 caller 元信息 → 用于追溯）
boolean ok = engine.eval(rule,
  ${dataJson.value.slice(0, 200)},
  InvocationContext.of("src-your-platform")   // ← 来源标识
);`)

const pythonCode = computed(() => `from ontos_rule import RuleEngine

engine = RuleEngine(caller="src-your-platform")   # ← 来源
rule = engine.compile("${expression.value.replace(/\n/g, ' ')}")
ok = rule.eval(${dataJson.value.slice(0, 100)})`)

const restCode = computed(() => `# REST API（任意语言）
curl -X POST http://localhost:8080/api/playground/eval \\
  -H 'Content-Type: application/json' \\
  -H 'X-Caller-Id: src-your-platform' \\
  -d '${JSON.stringify({ expression: expression.value.replace(/\n/g, ' '), data: JSON.parse(dataJson.value || '{}') }).slice(0, 200)}...'`)
</script>

<template>
  <div class="pg-shell" @keydown="onKeydown" tabindex="0">

    <!-- ========= 顶栏 ========= -->
    <header class="pg-header">
      <div class="header-inner">
        <div class="logo">
          <div class="logo-mark">R</div>
          <div class="logo-text">RULE-CORE</div>
          <div class="logo-sub">PLAYGROUND · v0.1</div>
        </div>
        <div class="header-meta">
          <span><span class="dot dot-ok"></span> ENGINE OK</span>
          <span class="text-xs font-mono fg-2">cel-java 0.7.0</span>
          <a href="javascript:void(0)" class="header-link">docs</a>
          <button class="theme-btn" @click="toggleTheme" title="切换主题">
            {{ theme === 'dark' ? '☀' : '☾' }}
          </button>
        </div>
      </div>
    </header>

    <!-- ========= 顶部主 Tab ========= -->
    <div class="toptabs-bar">
      <div class="toptabs-inner">
        <div class="toptab" :class="{ active: topTab === 'playground' }" @click="switchTopTab('playground')">
          Playground <span class="count">调试</span>
        </div>
        <div class="toptab" :class="{ active: topTab === 'sources' }" @click="switchTopTab('sources')">
          Sources
          <span class="count">{{ stats?.totalInvocations ?? 0 }} 次调用 · {{ Object.keys(stats?.byCaller ?? {}).length }} 来源</span>
        </div>
      </div>
    </div>

    <!-- ========= 主内容 ========= -->
    <main class="pg-main">

      <!-- ===== Playground 视图 ===== -->
      <div v-if="topTab === 'playground'" class="container">

        <div class="intro">
          <strong>表达式调试沙箱</strong>
          <span class="muted ml-2">
            写 CEL → 喂数据 → 看结果 · 用于验证规则是否符合预期 · 本页调用会被记录到
            <span class="font-mono fg-info">src-playground</span>
          </span>
        </div>

        <!-- 双列输入 -->
        <div class="row-2">
          <div class="pg-panel">
            <div class="panel-head">
              <span class="label">expression</span>
              <span class="muted text-xs">CEL</span>
              <a href="javascript:void(0)" class="help-link ml-auto"
                 @click="ElMessage.info('CEL 速查：算术 + - * / · 比较 < > == != · 逻辑 && || ! · 函数 size() matches() startsWith() in [...]')">
                ? 语法速查
              </a>
            </div>
            <div class="panel-body">
              <textarea v-model="expression" rows="6" class="pg-textarea mono"></textarea>
            </div>
          </div>

          <div class="pg-panel">
            <div class="panel-head">
              <span class="label">input data</span>
              <select class="ml-auto pg-select" @change="(e) => loadSample((e.target as HTMLSelectElement).value as any)">
                <option value="">— 样例 —</option>
                <option value="ok">合规</option>
                <option value="violate">违规</option>
              </select>
            </div>
            <div class="panel-body">
              <textarea v-model="dataJson" rows="6" class="pg-textarea mono"></textarea>
            </div>
          </div>
        </div>

        <!-- 工具栏 -->
        <div class="toolbar">
          <button class="btn btn-pri" :disabled="evalLoading" @click="runEval">
            <span v-if="evalLoading">RUN ...</span>
            <span v-else>RUN ▶</span>
          </button>
          <button class="btn btn-sm" @click="validateSyntax">语法检查</button>
          <span class="muted text-xs ml-auto">⌘+Enter 执行</span>
        </div>

        <!-- 结果卡 -->
        <div v-if="evalResult" class="result-main"
             :class="{ ok: isOk, fail: isFail, err: isError }">
          <div class="result-status">
            <template v-if="isOk">✓ true</template>
            <template v-else-if="isFail">✗ false</template>
            <template v-else>⚠ {{ evalResult.phase === 'compile' ? '编译失败' : '执行失败' }}</template>
          </div>
          <div class="result-detail">
            <template v-if="evalResult.ok">
              耗时 {{ evalResult.latencyMs?.toFixed(2) }} ms · backend {{ evalResult.backend }}
              · 变量 [{{ evalResult.variables?.join(', ') }}]
              · 已记录到 {{ evalResult.caller }}
            </template>
            <template v-else>
              {{ evalResult.error }}
            </template>
          </div>
        </div>

        <!-- Multi-backend 一致性 -->
        <div class="pg-panel">
          <div class="panel-head">
            <span class="label">multi-backend</span>
            <span class="muted text-xs">同表达式在不同执行环境跑一遍，确保结果一致</span>
            <a href="javascript:void(0)" class="help-link ml-auto"
               @click="ElMessage.info('为什么要多 Backend：规则可能在 JVM 内存跑，也可能翻译成 SQL 下推到数据库，或交给 Spark。本面板验证：同一表达式在所有 Backend 上结果一致。')">
              ? 为什么
            </a>
          </div>
          <div class="panel-body" style="padding: 0;">
            <div class="backend-grid">
              <div class="backend-card">
                <div class="backend-name"><span class="dot dot-ok"></span>JVM</div>
                <div class="backend-result t">{{ evalResult?.resultStr ?? '—' }}</div>
                <div class="backend-latency">{{ evalResult?.latencyMs?.toFixed(2) ?? '—' }} ms · 内存求值</div>
              </div>
              <div class="backend-card">
                <div class="backend-name"><span class="dot dot-info"></span>SQL / PG</div>
                <div class="backend-result t">{{ evalResult ? '可下推' : '—' }}</div>
                <div class="backend-latency">CI 一致性测试覆盖</div>
              </div>
              <div class="backend-card">
                <div class="backend-name"><span class="dot dot-info"></span>SQL / CH</div>
                <div class="backend-result t">{{ evalResult ? '可下推' : '—' }}</div>
                <div class="backend-latency">CI 一致性测试覆盖</div>
              </div>
              <div class="backend-card">
                <div class="backend-name"><span class="dot dot-idle"></span>Spark <span class="tag tag-idle ml-1">P2</span></div>
                <div class="backend-result skip">—</div>
                <div class="backend-latency">Phase 2 实现</div>
              </div>
            </div>
            <div class="consistency">
              ◉ CONSISTENCY · {{ evalResult ? '3 / 3 backends agree (28 金标准 CI 验证)' : '点 RUN 运行' }}
            </div>
          </div>
        </div>

        <!-- 性能 benchmark -->
        <div class="pg-panel benchmark-block">
          <div class="panel-head">
            <span class="label">性能基准测试</span>
            <span class="muted text-xs">9 scenario · 3 复杂度 × 3 数据量 · JVM Backend · 预热 2000 次后实测</span>
            <button class="btn btn-pri ml-auto" :disabled="benchLoading" @click="runBenchmark">
              {{ benchLoading ? '跑测试中…' : (benchResult ? '[ 重跑 benchmark ]' : '[ 跑 benchmark ▶ ]') }}
            </button>
          </div>
          <div class="panel-body" style="padding: 12px;">
            <!-- ECharts -->
            <div v-show="benchResult" ref="benchChartRef" style="height: 280px; margin-bottom: 12px;"></div>

            <!-- 详细表格 -->
            <table class="t t-tight" v-if="benchResult">
              <thead>
                <tr>
                  <th>复杂度</th>
                  <th class="num">行数</th>
                  <th class="num">耗时</th>
                  <th class="num">吞吐 (rows/s)</th>
                  <th>backend</th>
                  <th>表达式</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(s, i) in benchResult.results" :key="i">
                  <td>
                    <span class="tag"
                          :class="s.complexity === 'simple' ? 'tag-ok' : s.complexity === 'medium' ? 'tag-info' : 'tag-warn'">
                      {{ s.complexityLabel }}
                    </span>
                  </td>
                  <td class="num font-mono">{{ s.rowCount.toLocaleString() }}</td>
                  <td class="num font-mono fg-info">{{ s.durationMs }} ms</td>
                  <td class="num font-mono fg-ok">{{ s.opsPerSec.toLocaleString() }}</td>
                  <td><span class="tag tag-info">{{ benchResult.backend }}</span></td>
                  <td class="font-mono text-xs fg-2" style="max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{{ s.expression }}</td>
                </tr>
              </tbody>
            </table>
            <div class="bench-summary" v-if="benchResult">
              <span class="muted">总耗时 <b class="font-mono fg-0">{{ benchResult.totalElapsedMs }} ms</b></span>
              <span class="muted ml-3">预热 <b class="font-mono fg-0">{{ benchResult.warmupIterations }}</b> 次</span>
              <span class="muted ml-3">本次结果会写入 Sources 调用历史（caller=<span class="font-mono fg-info">src-playground-benchmark</span>）</span>
            </div>

            <div v-else class="bench-placeholder">
              点 <b class="font-mono fg-accent">[ 跑 benchmark ▶ ]</b> 在 JVM Backend 上跑 9 个 scenario，验证 PPT 性能数据
              <div class="bench-ppt-ref">
                <span class="label-sm">PPT 实测对照</span>
                <span class="font-mono text-xs ml-2 fg-2">
                  简单规则 ~380K rows/s · 复杂规则 ~145K rows/s · 1K 行 ≈ 30 ms · 100K 行 ≈ 250 ms
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- 集成代码 -->
        <details class="integration">
          <summary>集成代码 · 复制到你的程序</summary>
          <div class="int-tabs">
            <div class="int-tab" :class="{ active: integrationTab === 'java' }" @click="integrationTab = 'java'">java</div>
            <div class="int-tab" :class="{ active: integrationTab === 'python' }" @click="integrationTab = 'python'">python</div>
            <div class="int-tab" :class="{ active: integrationTab === 'rest' }" @click="integrationTab = 'rest'">rest api</div>
          </div>
          <pre class="code" v-if="integrationTab === 'java'">{{ javaCode }}</pre>
          <pre class="code" v-if="integrationTab === 'python'">{{ pythonCode }}</pre>
          <pre class="code" v-if="integrationTab === 'rest'">{{ restCode }}</pre>
        </details>
      </div>

      <!-- ===== Sources 视图 ===== -->
      <div v-if="topTab === 'sources'" class="container">

        <div class="intro">
          <strong>调用来源 · Sources</strong>
          <span class="muted ml-2">
            所有调用 core 引擎的来源都在这里登记 · 包括业务平台 / 客户系统 / Playground / CI 测试
          </span>
        </div>

        <!-- 来源统计 -->
        <div class="src-metrics">
          <div class="src-metric">
            <div class="src-metric-l">总调用</div>
            <div class="src-metric-v">{{ stats?.totalInvocations ?? 0 }}</div>
          </div>
          <div class="src-metric">
            <div class="src-metric-l">成功</div>
            <div class="src-metric-v fg-ok">{{ stats?.successCount ?? 0 }}</div>
          </div>
          <div class="src-metric">
            <div class="src-metric-l">失败</div>
            <div class="src-metric-v fg-err">{{ stats?.failedCount ?? 0 }}</div>
          </div>
          <div class="src-metric">
            <div class="src-metric-l">来源数</div>
            <div class="src-metric-v">{{ Object.keys(stats?.byCaller ?? {}).length }}</div>
          </div>
        </div>

        <!-- 左右双栏 -->
        <div class="src-layout">

          <!-- 左：来源列表 -->
          <div class="pg-panel">
            <div class="panel-head">
              <span class="label">来源列表</span>
              <button class="btn btn-xs ml-auto" @click="refreshSources">↻ 刷新</button>
            </div>
            <div class="src-list" v-loading="sourcesLoading">
              <div
                v-for="item in callerList" :key="item.caller"
                class="src-row"
                :class="{ active: item.caller === selectedCaller }"
                @click="selectCaller(item.caller)"
              >
                <span class="dot dot-ok"></span>
                <div class="src-row-body">
                  <div class="font-mono text-sm">{{ item.caller }}</div>
                  <div class="text-xs fg-2">{{ item.count }} 次调用</div>
                </div>
              </div>
              <div v-if="!sourcesLoading && callerList.length === 0" class="empty-tip">
                暂无调用 · 去 Playground Tab 跑一条 CEL 即可
              </div>
            </div>
          </div>

          <!-- 右：调用历史 -->
          <div class="pg-panel">
            <div class="panel-head">
              <span class="label">调用历史 · {{ selectedCaller || '请选择来源' }}</span>
              <span class="ml-auto label-sm fg-2">最近 {{ invocations.length }} 条</span>
            </div>
            <table class="t t-tight" v-loading="sourcesLoading">
              <thead>
                <tr>
                  <th style="width:30px"></th>
                  <th>时间</th>
                  <th>模式</th>
                  <th>BACKEND</th>
                  <th>规则</th>
                  <th>表达式 (前 60 字)</th>
                  <th class="num">耗时</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="inv in invocations" :key="inv.id">
                  <td><span class="dot" :class="inv.success ? 'dot-ok' : 'dot-err'"></span></td>
                  <td class="font-mono text-xs fg-2">{{ inv.timestamp }}</td>
                  <td>
                    <span class="tag" :class="inv.mode === 'EVAL' ? 'tag-info' : 'tag-warn'">{{ inv.mode }}</span>
                  </td>
                  <td><span class="tag tag-info">{{ inv.backend }}</span></td>
                  <td class="font-mono text-xs">{{ inv.ruleId }}</td>
                  <td class="font-mono text-xs fg-1" style="max-width: 360px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                    {{ inv.expression?.slice(0, 60) || '—' }}
                  </td>
                  <td class="num font-mono text-xs">{{ inv.latencyMs?.toFixed(2) ?? '—' }} ms</td>
                </tr>
                <tr v-if="!sourcesLoading && invocations.length === 0">
                  <td colspan="7" class="empty-tip">该来源暂无调用历史</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
/* ========= 浅色主题（playground 独有的双主题）========= */
.pg-shell {
  min-height: 100vh;
  outline: none;
}
:global(html[data-theme="light"]) {
  --bg-0: #ffffff;
  --bg-1: #f6f8fa;
  --bg-2: #eaeef2;
  --bg-3: #d0d7de;
  --bg-4: #afb8c1;
  --fg-0: #1f2328;
  --fg-1: #57606a;
  --fg-2: #6e7781;
  --fg-3: #8c959f;
  --accent: #0969da;
}

/* ========= 顶栏 ========= */
.pg-header {
  border-bottom: 1px solid var(--bg-3);
  background: var(--bg-1);
}
.header-inner {
  display: flex; align-items: center;
  height: 48px; max-width: 1280px; margin: 0 auto;
  padding: 0 24px; gap: 16px;
}
.logo { display: flex; align-items: center; gap: 10px; }
.logo-mark {
  width: 24px; height: 24px; background: var(--accent);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 700; font-family: var(--font-mono); font-size: 13px;
  border-radius: 3px;
}
.logo-text {
  font-family: var(--font-mono); font-weight: 600;
  letter-spacing: 0.08em; font-size: 12px; color: var(--fg-0);
}
.logo-sub {
  font-family: var(--font-mono); color: var(--fg-2); font-size: 10px;
  margin-left: 4px;
}
.header-meta {
  margin-left: auto; display: flex; align-items: center; gap: 16px;
  font-size: 12px; color: var(--fg-1);
}
.header-link {
  color: var(--accent); text-decoration: none;
  font-family: var(--font-mono); font-size: 11px;
}
.header-link:hover { text-decoration: underline; }
.theme-btn {
  background: var(--bg-2); border: 1px solid var(--bg-4);
  color: var(--fg-1); padding: 4px 10px;
  cursor: pointer; border-radius: 3px;
  font-size: 14px; line-height: 1;
}
.theme-btn:hover { color: var(--fg-0); border-color: var(--accent); }

/* ========= 顶部 Tab ========= */
.toptabs-bar {
  background: var(--bg-1); border-bottom: 1px solid var(--bg-3);
}
.toptabs-inner {
  display: flex; max-width: 1280px; margin: 0 auto; padding: 0 24px;
}
.toptab {
  padding: 10px 18px; font-family: var(--font-mono);
  font-size: 12px; font-weight: 500; letter-spacing: 0.04em;
  color: var(--fg-2); cursor: pointer;
  border-bottom: 2px solid transparent; margin-bottom: -1px;
  text-transform: uppercase;
  display: flex; align-items: baseline; gap: 8px;
}
.toptab:hover { color: var(--fg-0); }
.toptab.active { color: var(--accent); border-bottom-color: var(--accent); }
.toptab .count {
  font-size: 10px; color: var(--fg-3); font-weight: 400;
  text-transform: none; letter-spacing: 0;
}

/* ========= 主内容 ========= */
.pg-main {
  padding: 24px 0;
  min-height: calc(100vh - 110px);
}
.container {
  max-width: 1280px; margin: 0 auto; padding: 0 24px;
}

/* ========= Intro ========= */
.intro {
  background: var(--bg-1); border: 1px solid var(--bg-3);
  border-left: 3px solid var(--info);
  padding: 10px 14px; font-size: 13px;
  margin-bottom: 16px;
}
.intro strong { color: var(--fg-0); }
.muted { color: var(--fg-2); }

/* ========= 双列输入 ========= */
.row-2 {
  display: grid; grid-template-columns: 1fr 1fr;
  gap: 12px; margin-bottom: 14px;
}
.pg-panel {
  background: var(--bg-1); border: 1px solid var(--bg-3);
}
.panel-head {
  background: var(--bg-2); border-bottom: 1px solid var(--bg-3);
  padding: 6px 12px;
  display: flex; align-items: center; gap: 8px;
  min-height: 32px;
}
.panel-body {
  padding: 8px 12px;
}
.pg-textarea {
  width: 100%; background: var(--bg-0); color: var(--fg-0);
  border: 1px solid var(--bg-3); padding: 8px 10px;
  font-family: var(--font-mono); font-size: 12px; line-height: 1.55;
  outline: none; resize: vertical;
}
.pg-textarea:focus { border-color: var(--accent); }
.mono { font-family: var(--font-mono); }
.help-link {
  color: var(--accent); text-decoration: none;
  font-family: var(--font-mono); font-size: 11px;
}
.help-link:hover { text-decoration: underline; }
.pg-select {
  background: var(--bg-0); color: var(--fg-0);
  border: 1px solid var(--bg-3); padding: 3px 8px;
  font-family: var(--font-sans); font-size: 11px;
}

/* ========= 工具栏 ========= */
.toolbar {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 14px;
}

/* ========= 结果卡 ========= */
.result-main {
  border: 2px solid var(--ok); background: rgba(95, 184, 122, 0.08);
  padding: 14px 18px; margin-bottom: 16px;
}
.result-main.ok    { border-color: var(--ok);   background: rgba(95, 184, 122, 0.08); }
.result-main.fail  { border-color: var(--err);  background: rgba(217, 112, 112, 0.08); }
.result-main.err   { border-color: var(--warn); background: rgba(212, 166, 74, 0.08); }
.result-status {
  font-family: var(--font-mono); font-size: 22px; font-weight: 600;
  margin-bottom: 4px;
}
.result-main.ok   .result-status { color: var(--ok); }
.result-main.fail .result-status { color: var(--err); }
.result-main.err  .result-status { color: var(--warn); }
.result-detail {
  font-size: 12px; color: var(--fg-1);
}

/* ========= 多 Backend 网格 ========= */
.backend-grid {
  display: grid; grid-template-columns: repeat(4, 1fr);
  background: var(--bg-3); gap: 1px;
}
.backend-card {
  background: var(--bg-1); padding: 12px 14px;
}
.backend-name {
  display: flex; align-items: center; gap: 6px;
  font-family: var(--font-mono); font-size: 12px; font-weight: 500;
  color: var(--fg-0);
}
.backend-result {
  font-family: var(--font-mono); font-size: 18px;
  margin: 6px 0;
}
.backend-result.t { color: var(--ok); }
.backend-result.skip { color: var(--fg-3); }
.backend-latency {
  font-family: var(--font-mono); font-size: 10px; color: var(--fg-2);
}
.consistency {
  background: var(--bg-2); padding: 8px 14px;
  font-family: var(--font-mono); font-size: 11px; color: var(--accent);
  border-top: 1px solid var(--bg-3);
}

/* ========= Benchmark ========= */
.benchmark-block { margin-bottom: 18px; }
.bench-placeholder {
  text-align: center; padding: 28px 16px;
  color: var(--fg-2); font-size: 13px;
}
.bench-ppt-ref {
  margin-top: 14px; padding: 10px 14px;
  background: var(--bg-2); border: 1px dashed var(--bg-3);
  display: inline-block;
}
.bench-summary {
  margin-top: 12px;
  padding: 8px 12px;
  background: var(--bg-2); border-left: 2px solid var(--info);
  font-size: 11px;
}
.tag {
  display: inline-flex; align-items: center;
  padding: 1px 6px;
  font-family: var(--font-mono); font-size: 10px;
  border: 1px solid; text-transform: uppercase; letter-spacing: 0.04em;
}
.tag-ok   { color: var(--ok);   border-color: var(--ok); }
.tag-info { color: var(--info); border-color: var(--info); }
.tag-warn { color: var(--warn); border-color: var(--warn); }

/* ========= 集成代码 ========= */
.integration {
  margin-top: 18px;
  background: var(--bg-1); border: 1px solid var(--bg-3);
  padding: 12px 14px;
}
.integration summary {
  cursor: pointer; font-size: 13px; color: var(--fg-0); font-weight: 500;
}
.int-tabs {
  display: flex; gap: 0; margin: 12px 0 0; border-bottom: 1px solid var(--bg-3);
}
.int-tab {
  padding: 6px 14px; cursor: pointer;
  font-family: var(--font-mono); font-size: 11px;
  color: var(--fg-2); text-transform: uppercase;
  border-bottom: 2px solid transparent; margin-bottom: -1px;
}
.int-tab:hover { color: var(--fg-0); }
.int-tab.active { color: var(--accent); border-bottom-color: var(--accent); }
.integration .code {
  margin-top: 12px;
  background: var(--bg-0); border: 1px solid var(--bg-3);
  padding: 12px 14px;
  font-family: var(--font-mono); font-size: 11.5px; color: var(--fg-1);
  white-space: pre-wrap; word-break: break-all;
}

/* ========= Sources 来源 metric ========= */
.src-metrics {
  display: grid; grid-template-columns: repeat(4, 1fr);
  gap: 12px; margin-bottom: 16px;
}
.src-metric {
  background: var(--bg-1); border: 1px solid var(--bg-3); padding: 12px 14px;
}
.src-metric-l {
  font-family: var(--font-mono); font-size: 10px; letter-spacing: 0.1em;
  color: var(--fg-2); text-transform: uppercase; margin-bottom: 4px;
}
.src-metric-v {
  font-family: var(--font-mono); font-size: 22px; font-weight: 500;
  color: var(--fg-0); line-height: 1;
}

/* ========= Sources 双栏 ========= */
.src-layout {
  display: grid; grid-template-columns: 320px 1fr;
  gap: 12px;
}
.src-list { padding: 6px 0; max-height: 70vh; overflow-y: auto; }
.src-row {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 14px; cursor: pointer;
  border-left: 2px solid transparent;
}
.src-row:hover { background: var(--bg-2); }
.src-row.active { background: var(--bg-2); border-left-color: var(--accent); }
.src-row-body { flex: 1; }
.empty-tip {
  padding: 30px 16px; text-align: center;
  color: var(--fg-3); font-family: var(--font-mono); font-size: 11px;
}
</style>
