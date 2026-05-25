<script setup lang="ts">
interface Props {
  icon?: string
  title?: string
  subtitle?: string
}

withDefaults(defineProps<Props>(), {
  icon: '∅',
  title: '暂无数据',
  subtitle: '',
})
</script>

<template>
  <div class="empty">
    <div class="empty-icon">{{ icon }}</div>
    <div class="empty-title">{{ title }}</div>
    <div v-if="subtitle" class="empty-subtitle">{{ subtitle }}</div>
    <div class="empty-actions">
      <slot name="actions" />
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
}
</style>
