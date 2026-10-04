<template>
  <el-dialog
    v-model="dialogVisible"
    title="批量隐藏消息"
    width="480px"
    :close-on-click-modal="false"
  >
    <div class="batch-hide-body">
      <div class="depth-range-row">
        <span class="depth-label">隐藏深度范围：</span>
        <el-input-number v-model="depthFrom" :min="0" :step="1" style="width: 110px" />
        <span class="depth-sep">—</span>
        <el-input-number v-model="depthTo" :min="0" :step="1" style="width: 110px" />
      </div>

      <div class="preview-info">
        <template v-if="matchCount === null">
          <span class="preview-hint">输入范围后自动预览</span>
        </template>
        <template v-else-if="matchCount === 0">
          <span class="preview-empty">该范围内没有可隐藏的消息</span>
        </template>
        <template v-else>
          <span class="preview-count">
            将隐藏 <strong>{{ matchCount }}</strong> 条消息
          </span>
          <span v-if="hasToolInRange" class="preview-warning">
            ⚠️ 包含工具消息，隐藏时将级联影响配对的 ASSISTANT 消息
          </span>
        </template>
      </div>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button
        type="primary"
        @click="handleConfirm"
        :loading="hiding"
        :disabled="matchCount === null || matchCount === 0"
      >
        隐藏 {{ matchCount || 0 }} 条消息
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { conversationApi } from '../api/conversation'
import type { Message } from '../types'

const emit = defineEmits(['done'])

const dialogVisible = ref(false)
const hiding = ref(false)
const depthFrom = ref(0)
const depthTo = ref(50)

let currentMessages: Message[] = []
let currentConversationId = ''

/** 匹配的消息索引（仅可见的） */
const matchResult = computed(() => {
  if (currentMessages.length === 0) return null
  const from = Math.min(depthFrom.value, depthTo.value)
  const to = Math.max(depthFrom.value, depthTo.value)

  const indexes: number[] = []
  let hasTool = false

  for (let i = 0; i < currentMessages.length; i++) {
    const d = currentMessages[i].depth ?? 0
    if (d >= from && d <= to && currentMessages[i].isSeeable !== false) {
      indexes.push(i)
      if (currentMessages[i].role === 'TOOL') hasTool = true
    }
  }

  return { indexes, hasTool }
})

const matchCount = computed(() => matchResult.value?.indexes.length ?? null)
const hasToolInRange = computed(() => matchResult.value?.hasTool ?? false)

/** 当范围变化时不做额外操作，matchResult 自动重新计算 */

const open = (conversationId: string, messages: Message[]) => {
  currentConversationId = conversationId
  currentMessages = messages
  depthFrom.value = 0
  depthTo.value = 50
  dialogVisible.value = true
}

const handleConfirm = async () => {
  const result = matchResult.value
  if (!result || result.indexes.length === 0) return

  if (result.hasTool) {
    try {
      await ElMessageBox.confirm(
        `将隐藏 ${result.indexes.length} 条消息。其中包含工具消息，隐藏时将级联影响配对的 ASSISTANT 消息。确定要继续吗？`,
        '批量隐藏确认',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
  }

  hiding.value = true
  try {
    const res = await conversationApi.hideMessages({
      conversationId: currentConversationId,
      messageIndexes: result.indexes
    })
    if (res.code === 1) {
      ElMessage.success(`已隐藏 ${result.indexes.length} 条消息`)
      dialogVisible.value = false
      emit('done')
    } else {
      ElMessage.error(res.msg || '隐藏失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.msg || '隐藏失败')
  } finally {
    hiding.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.batch-hide-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.depth-range-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
}

.depth-label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  flex-shrink: 0;
}

.depth-sep {
  color: var(--color-text-tertiary);
  font-weight: var(--font-weight-medium);
}

.preview-info {
  text-align: center;
  padding: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-bg-page);
  min-height: 48px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
}

.preview-hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.preview-empty {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.preview-count {
  font-size: var(--font-size-base);
  color: var(--color-text-primary);
  font-weight: var(--font-weight-medium);
}

.preview-count strong {
  color: var(--color-text-primary);
  font-weight: var(--font-weight-bold);
  font-size: var(--font-size-lg);
}

.preview-warning {
  font-size: var(--font-size-xs);
  color: var(--color-warning);
  line-height: var(--line-height-relaxed);
}
</style>
