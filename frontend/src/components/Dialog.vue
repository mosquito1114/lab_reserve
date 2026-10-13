<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    :top="top"
    :close-on-click-modal="closeOnClickModal"
    :destroy-on-close="destroyOnClose"
    append-to-body
    @closed="handleClosed"
  >
    <slot />

    <template #footer>
      <slot name="footer">
        <el-button @click="handleCancel">{{ cancelText }}</el-button>
        <el-button type="primary" :loading="confirmLoading" @click="handleConfirm">
          {{ confirmText }}
        </el-button>
      </slot>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 通用弹窗。
 *
 * 统一全站弹窗的宽度、按钮文案与"确认中"状态，
 * 避免每个页面各写一份 el-dialog 导致样式与交互不一致。
 *
 * 用法：
 * <pre>
 * <CommonDialog v-model="visible" title="新增用户" :confirm-loading="saving" @confirm="handleSave">
 *   ...表单...
 * </CommonDialog>
 * </pre>
 */
const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    width?: string | number
    top?: string
    confirmText?: string
    cancelText?: string
    confirmLoading?: boolean
    closeOnClickModal?: boolean
    destroyOnClose?: boolean
  }>(),
  {
    title: '提示',
    width: '600px',
    top: '8vh',
    confirmText: '确定',
    cancelText: '取消',
    confirmLoading: false,
    closeOnClickModal: false,
    destroyOnClose: true
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
  (e: 'cancel'): void
  (e: 'closed'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

function handleConfirm(): void {
  emit('confirm')
}

function handleCancel(): void {
  visible.value = false
  emit('cancel')
}

function handleClosed(): void {
  emit('closed')
}
</script>
