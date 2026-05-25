<script setup lang="ts">
import { ref } from 'vue'
import { adminApi } from '@/api/admin'

interface Props {
  icon?: string
  title?: string
  subtitle?: string
  /** 是否显示"重置 demo 数据"按钮 */
  showReset?: boolean
}

withDefaults(defineProps<Props>(), {
  icon: '∅',
  title: '暂无数据',
  subtitle: '',
  showReset: true,
})

const emit = defineEmits<{ reseeded: [] }>()

const loading = ref(false)

async function reseed() {
  loading.value = true
  try {
    const r = await adminApi.reseed(true)
    ElMessage.success(
      `${r.message} · ${r.rules} 规则 · ${r.runs} 执行 · ${r.scores} 评分`
    )
    emit('reseeded')
    ;(window as any).__refreshMetrics?.()
  } finally { loading.value = false }
}
</script>

<template>
  <div class="empty">
    <div class="empty-icon">{{ icon }}</div>
    <div class="empty-title">{{ title }}</div>
    <div v-if="subtitle" class="empty-subtitle">{{ subtitle }}</div>
    <div class="empty-actions">
      <slot name="actions" />
      <button v-if="showReset" class="btn btn-pri btn-sm" :disabled="loading" @click="reseed">
        {{ loading ? '正在重置…' : '[ 一键加载 demo 数据 ]' }}
      </button>
    </div>
    <div class="empty-hint">
      会清空当前所有 mock 数据后重新种入 18 条规则 · 200 条执行历史 · 24 条评分快照
    </div>
  </div>
</template>

<style scoped>
.empty {
  text-align: center;
  padding: 56px 24px;
  color: var(--fg-2);
}
.empty-icon {
  font-size: 48px;
  color: var(--fg-3);
  font-family: var(--font-mono);
  margin-bottom: 16px;
  user-select: none;
}
.empty-title {
  font-size: 14px;
  color: var(--fg-0);
  font-weight: 500;
  margin-bottom: 4px;
}
.empty-subtitle {
  font-size: 12px;
  color: var(--fg-2);
  margin-bottom: 18px;
}
.empty-actions {
  display: flex; gap: 8px; justify-content: center;
  margin-bottom: 12px;
}
.empty-hint {
  font-size: 11px;
  color: var(--fg-3);
  font-family: var(--font-mono);
}
</style>
