<template>
  <el-dialog
    v-model="dialogVisible"
    title="注入提示词"
    width="500px"
    :close-on-click-modal="false"
    @open="loadPrompts"
  >
    <div v-if="loading" class="dialog-loading">
      <el-icon class="is-loading"><Setting /></el-icon>
      <span>加载中...</span>
    </div>

    <template v-else>
      <div v-if="prompts.length === 0" class="dialog-empty">
        <p>暂无注入提示词</p>
        <p class="dialog-empty-hint">请在"管理 → 注入提示词"中创建</p>
      </div>

      <el-checkbox-group v-else v-model="selected" class="dialog-insert-list">
        <label
          v-for="prompt in prompts"
          :key="prompt.id"
          class="dialog-insert-item"
          :class="{ 'is-checked': selected.includes(prompt.id) }"
        >
          <el-checkbox :label="prompt.id" :value="prompt.id">
            <span class="dialog-insert-name">{{ prompt.promptName }}</span>
          </el-checkbox>
          <el-tag size="small" type="info" effect="plain">D{{ prompt.depth }}</el-tag>
          <span class="dialog-insert-content">{{ truncate(prompt.content, 80) }}</span>
        </label>
      </el-checkbox-group>
    </template>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleConfirm">
        确定{{ selected.length > 0 ? ` (${selected.length})` : '' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { insertPromptApi } from '../api/insertPrompt'
import type { InsertPrompt } from '../types'

const emit = defineEmits<{
  (e: 'confirm', ids: number[]): void
}>()

const dialogVisible = ref(false)
const loading = ref(false)
const prompts = ref<InsertPrompt[]>([])
const selected = ref<number[]>([])

const truncate = (text: string, maxLen: number): string => {
  if (!text) return ''
  return text.length > maxLen ? text.substring(0, maxLen) + '...' : text
}

const loadPrompts = async () => {
  loading.value = true
  try {
    const userId = localStorage.getItem('userId') || 'default_user'
    const res = await insertPromptApi.list(userId)
    if (res.code === 1 && res.data) {
      prompts.value = res.data
    }
  } catch (error: any) {
    ElMessage.error(error?.msg || '加载失败')
  } finally {
    loading.value = false
  }
}

const open = (currentIds: number[]) => {
  selected.value = [...currentIds]
  dialogVisible.value = true
}

const handleConfirm = () => {
  emit('confirm', [...selected.value])
  dialogVisible.value = false
}

defineExpose({ open })
</script>

<style scoped>
.dialog-loading,
.dialog-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-8);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
  gap: var(--space-2);
}

.dialog-empty p {
  margin: 0;
}

.dialog-empty-hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.dialog-insert-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  max-height: 360px;
  overflow-y: auto;
}

.dialog-insert-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--transition-fast),
              border-color var(--transition-fast),
              box-shadow var(--transition-fast);
  background: var(--color-bg-surface);
}

.dialog-insert-item:hover {
  background-color: var(--color-bg-page);
  border-color: var(--color-border);
}

.dialog-insert-item.is-checked {
  background-color: var(--color-primary-light);
  border-color: var(--color-primary-border);
  box-shadow: 0 0 0 1px var(--color-primary-border);
}

.dialog-insert-item :deep(.el-checkbox) {
  margin-right: 0;
  flex-shrink: 0;
}

.dialog-insert-name {
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
}

.dialog-insert-content {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
</style>
