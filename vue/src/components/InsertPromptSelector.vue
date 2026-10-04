<template>
  <el-popover
    placement="top-start"
    :width="350"
    trigger="click"
    :disabled="disabled"
    @show="loadPrompts"
  >
    <template #reference>
      <el-button text :disabled="disabled" class="insert-selector-btn">
        <el-icon><DocumentAdd /></el-icon>
        注入提示词
        <el-badge
          v-if="selectedIds.length > 0"
          :value="selectedIds.length"
          class="insert-badge"
          type="primary"
        />
      </el-button>
    </template>

    <div class="insert-popover-content">
      <!-- 加载态 -->
      <div v-if="loading" class="popover-loading">
        <el-icon class="is-loading"><Setting /></el-icon>
        <span>加载中...</span>
      </div>

      <!-- 空态 -->
      <div v-else-if="prompts.length === 0" class="popover-empty">
        <p>暂无注入提示词</p>
        <el-button text size="small" type="primary" @click="$emit('openManager')">
          前往管理
        </el-button>
      </div>

      <!-- 列表 -->
      <el-checkbox-group v-else v-model="selectedIds" class="insert-list">
        <label
          v-for="prompt in prompts"
          :key="prompt.id"
          class="insert-item"
          :class="{ 'is-checked': selectedIds.includes(prompt.id) }"
        >
          <el-checkbox :label="prompt.id" :value="prompt.id">
            <div class="insert-item-info">
              <span class="insert-item-name">{{ prompt.promptName }}</span>
              <el-tag size="small" type="info" effect="plain" class="insert-item-depth">
                D{{ prompt.depth }}
              </el-tag>
            </div>
            <span class="insert-item-content">{{ truncate(prompt.content, 50) }}</span>
          </el-checkbox>
        </label>
      </el-checkbox-group>

      <el-divider style="margin: var(--space-2) 0" />
      <div class="popover-footer">
        <el-button text size="small" @click="$emit('openManager')">
          <el-icon><Setting /></el-icon>
          管理提示词
        </el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { DocumentAdd, Setting } from '@element-plus/icons-vue'
import { insertPromptApi } from '../api/insertPrompt'
import type { InsertPrompt } from '../types'

const props = defineProps<{
  modelValue: number[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number[]]
  'openManager': []
}>()

const loading = ref(false)
const prompts = ref<InsertPrompt[]>([])

const selectedIds = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const userId = () => localStorage.getItem('userId') || 'default_user'

const truncate = (text: string, maxLen: number): string => {
  if (!text) return ''
  return text.length > maxLen ? text.substring(0, maxLen) + '...' : text
}

const loadPrompts = async () => {
  loading.value = true
  try {
    const res = await insertPromptApi.list(userId())
    if (res.code === 1 && res.data) {
      prompts.value = res.data
    }
  } catch {
    // 静默失败
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.insert-selector-btn {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  flex-shrink: 0;
  transition: color var(--transition-fast);
}

.insert-selector-btn:hover {
  color: var(--color-primary);
}

.insert-badge {
  margin-left: var(--space-1);
}

.insert-popover-content {
  font-size: var(--font-size-sm);
}

.popover-loading,
.popover-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-4);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.popover-empty p {
  margin: 0;
}

.insert-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  max-height: 280px;
  overflow-y: auto;
}

.insert-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--transition-fast),
              border-color var(--transition-fast);
  background: var(--color-bg-surface);
}

.insert-item:hover {
  background-color: var(--color-bg-page);
  border-color: var(--color-border);
}

.insert-item.is-checked {
  background-color: var(--color-primary-light);
  border-color: var(--color-primary-border);
}

.insert-item :deep(.el-checkbox) {
  width: 100%;
  align-items: flex-start;
}

.insert-item :deep(.el-checkbox__label) {
  width: 100%;
  padding-left: var(--space-2);
}

.insert-item-info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.insert-item-name {
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
}

.insert-item-depth {
  font-size: 10px;
  padding: 0 4px;
  height: 18px;
  line-height: 18px;
}

.insert-item-content {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
  padding-left: calc(var(--space-5) + var(--space-2));
}

.popover-footer {
  display: flex;
  justify-content: flex-start;
}
</style>
