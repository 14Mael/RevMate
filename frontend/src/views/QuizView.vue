<script setup lang="ts">
/**
 * AI 出题页
 * - 任务 5：选题型/题数 → 出题 → 答题 → 批改
 * - 设计依据：frontend-design-spec.md §7.6
 */
</script>

<template>
  <div class="placeholder">
    <div class="placeholder-icon">✏️</div>
    <h2 class="placeholder-title">AI 出题</h2>
    <p class="placeholder-desc">
      任务 5 在此实现：出题配置面板 + 沉浸式答题区 + 批改反馈。<br />
      设计依据见 frontend-design-spec.md §7.6。
    </p>
  </div>
</template>

<style scoped>
.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  text-align: center;
}
.placeholder-icon {
  font-size: 48px;
  margin-bottom: var(--space-md);
}
.placeholder-title {
  font-size: var(--font-size-h2);
  margin-bottom: var(--space-sm);
}
.placeholder-desc {
  color: var(--color-text-assist);
  font-size: var(--font-size-body);
  line-height: var(--line-height-body);
  max-width: 480px;
}
</style>
