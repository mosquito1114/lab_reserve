<template>
  <div class="page-header">
    <div class="page-header__text">
      <h2 class="page-header__title">{{ title }}</h2>
      <p v-if="subtitle" class="page-header__subtitle">{{ subtitle }}</p>
    </div>

    <div v-if="$slots.extra" class="page-header__extra">
      <slot name="extra" />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 页面标题区。
 *
 * 28 个业务页面原来都没有标题，进去只能靠面包屑判断自己在哪一页。
 * 统一插到 <div class="page-container"> 的第一个子节点位置，
 * 所有页面的顶部节奏由此统一。
 *
 * 已由 main.ts 全局注册，页面里直接写 <PageHeader title="..." /> 即可。
 */
defineProps<{
  /** 页面标题 */
  title: string
  /** 副标题：一句话说明这页在做什么 */
  subtitle?: string
}>()
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  gap: var(--lr-space-4);
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--lr-space-5);
}

.page-header__text {
  position: relative;
  padding-left: 14px;
}

/* 标题左侧的赭色竖条，给页面一个视觉起点 */
.page-header__text::before {
  position: absolute;
  top: 6px;
  bottom: 6px;
  left: 0;
  width: 3px;
  background: var(--lr-primary);
  border-radius: var(--lr-radius-pill);
  content: '';
}

.page-header__title {
  font-size: var(--lr-font-xl);
  font-weight: 600;
  line-height: 1.4;
  color: var(--lr-text-primary);
  letter-spacing: -0.02em;
}

.page-header__subtitle {
  margin-top: var(--lr-space-1);
  font-size: var(--lr-font-sm);
  line-height: 1.5;
  color: var(--lr-text-secondary);
}

.page-header__extra {
  display: flex;
  flex-shrink: 0;
  gap: var(--lr-space-2);
  align-items: center;
  padding-top: 2px;
}
</style>
