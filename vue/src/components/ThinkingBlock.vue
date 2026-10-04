<template>
  <div class="thinking-block" :class="{ 'is-streaming': isStreaming && !collapsed }">
    <div class="thinking-header" @click="toggle">
      <el-icon class="thinking-chevron" :class="{ expanded: !collapsed }">
        <ArrowRight />
      </el-icon>
      <span class="thinking-label">
        {{ isComplete ? '思考过程' : '正在思考中...' }}
      </span>
      <span v-if="isComplete" class="thinking-badge">可折叠</span>
    </div>
    <div class="thinking-content" :class="{ collapsed }">
      <div class="thinking-inner" v-html="displayContent"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'

const props = defineProps<{
  content: string
  isStreaming: boolean
  isComplete: boolean
}>()

const collapsed = ref(true)

/**
 * 对 HTML 特殊字符进行转义，防止 XSS。
 */
const escapeHtml = (text: string): string => {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

// 将字面 \n 转为 <br> 标签，确保换行在任何 CSS 环境下都能正确渲染
const displayContent = computed(() => {
  let processed = props.content.replace(/\\n/g, '\n')
  return escapeHtml(processed).replace(/\n/g, '<br>')
})

// 流式输出时自动展开，完成后自动折叠
watch(
  () => props.isStreaming,
  (streaming) => {
    if (streaming) {
      collapsed.value = false
    } else {
      collapsed.value = true
    }
  }
)

// 当 thinking 内容首次出现且正在流式输出时，自动展开
watch(
  () => props.content,
  (newVal, oldVal) => {
    if (!oldVal && newVal && props.isStreaming) {
      collapsed.value = false
    }
  }
)

function toggle() {
  collapsed.value = !collapsed.value
}
</script>

<style scoped>
.thinking-block {
  margin: var(--space-2) 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background-color: var(--color-bg-elevated, #f8f9fb);
  transition: border-color var(--transition-fast);
}

.thinking-block.is-streaming {
  border-color: var(--color-primary-border, #a0c4ff);
  box-shadow: 0 0 0 1px var(--color-primary-border, #a0c4ff);
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  cursor: pointer;
  user-select: none;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  transition: background-color var(--transition-fast),
              color var(--transition-fast);
}

.thinking-header:hover {
  background-color: var(--color-bg-surface-hover, rgba(0, 0, 0, 0.04));
  color: var(--color-text-primary);
}

.thinking-chevron {
  font-size: 14px;
  transition: transform 0.25s ease;
  flex-shrink: 0;
}

.thinking-chevron.expanded {
  transform: rotate(90deg);
}

.thinking-label {
  flex: 1;
  font-weight: var(--font-weight-medium);
}

.is-streaming .thinking-label {
  color: var(--color-primary);
}

.thinking-badge {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background-color: var(--color-bg-page, #eee);
  font-style: italic;
}

.thinking-content {
  overflow: hidden;
  transition: max-height 0.3s ease, padding 0.3s ease;
  max-height: 20000px;
}

.thinking-content.collapsed {
  max-height: 0;
}

.thinking-inner {
  padding: var(--space-3);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: var(--line-height-relaxed);
  white-space: pre-wrap;
  word-wrap: break-word;
  border-top: 1px solid var(--color-border-light);
  background-color: var(--color-bg-page, #f5f5f5);
  max-height: 500px;
  overflow-y: auto;
}
</style>
