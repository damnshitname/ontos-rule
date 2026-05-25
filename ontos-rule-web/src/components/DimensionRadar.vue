<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts/core'
import { RadarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([RadarChart, TitleComponent, TooltipComponent, LegendComponent, CanvasRenderer])

interface Props {
  scores: {
    completeness?: number | null
    uniqueness?: number | null
    validity?: number | null
    consistency?: number | null
    accuracy?: number | null
    timeliness?: number | null
  }
  /** 标签名 */
  name?: string
  /** 主色调 */
  color?: string
  /** 高度 */
  height?: string
}

const props = withDefaults(defineProps<Props>(), {
  name: '质量评分',
  color: '#5a93e6',
  height: '320px',
})

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

const radarOption = computed(() => {
  const s = props.scores
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item' },
    radar: {
      indicator: [
        { name: '完整性', max: 100 },
        { name: '唯一性', max: 100 },
        { name: '有效性', max: 100 },
        { name: '一致性', max: 100 },
        { name: '准确性', max: 100 },
        { name: '及时性', max: 100 },
      ],
      shape: 'polygon',
      splitNumber: 4,
      center: ['50%', '52%'],
      radius: '68%',
      axisName: {
        color: '#aab2c2',
        fontSize: 12,
        fontFamily: 'IBM Plex Mono, monospace',
      },
      splitLine: { lineStyle: { color: 'rgba(170, 178, 194, 0.15)' } },
      splitArea: {
        areaStyle: {
          color: ['rgba(90, 147, 230, 0.02)', 'rgba(90, 147, 230, 0.06)'],
        },
      },
      axisLine: { lineStyle: { color: 'rgba(170, 178, 194, 0.25)' } },
    },
    series: [
      {
        name: props.name,
        type: 'radar',
        symbol: 'circle',
        symbolSize: 6,
        itemStyle: { color: props.color },
        lineStyle: { color: props.color, width: 2 },
        areaStyle: { color: props.color, opacity: 0.25 },
        data: [
          {
            value: [
              Number(s.completeness ?? 0),
              Number(s.uniqueness ?? 0),
              Number(s.validity ?? 0),
              Number(s.consistency ?? 0),
              Number(s.accuracy ?? 0),
              Number(s.timeliness ?? 0),
            ],
            name: props.name,
          },
        ],
      },
    ],
  }
})

function ensureChart() {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value, undefined, { renderer: 'canvas' })
  }
  chart.setOption(radarOption.value, true)
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  ensureChart()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
  window.removeEventListener('resize', handleResize)
})

watch(() => props.scores, () => ensureChart(), { deep: true })
watch(() => props.color, () => ensureChart())
</script>

<template>
  <div ref="chartRef" :style="{ width: '100%', height: height }"></div>
</template>
