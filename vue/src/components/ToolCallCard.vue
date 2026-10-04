<template>
  <div class="tool-call-card" :class="[`status-${status}`, { collapsed: collapsed && status !== 'running' }]">
    <div class="tool-call-header" @click="toggle">
      <!-- 折叠箭头 -->
      <el-icon class="tool-chevron" :class="{ expanded: !collapsed }">
        <ArrowRight />
      </el-icon>
      <!-- 状态图标 -->
      <span class="tool-status-icon">
        <el-icon v-if="status === 'running'" class="is-loading spinning"><Setting /></el-icon>
        <el-icon v-else-if="status === 'done'"><Check /></el-icon>
        <el-icon v-else><Close /></el-icon>
      </span>
      <!-- 工具名称 -->
      <span class="tool-name">{{ displayName || call.functionName }}</span>
      <!-- 深度徽章 -->
      <el-tag v-if="depth !== undefined" size="small" type="info" effect="plain" class="depth-badge">
        D{{ depth }}
      </el-tag>
      <!-- 状态文字 -->
      <span class="tool-status-text">
        <template v-if="status === 'running'">执行中...</template>
        <template v-else-if="status === 'done'">已完成</template>
        <template v-else>出错</template>
      </span>
      <!-- 折叠标记 -->
      <span v-if="status === 'done'" class="tool-collapse-badge">可折叠</span>
    </div>

    <!-- 参数 & 结果 -->
    <div v-if="hasBody" class="tool-call-body" :class="{ collapsed: collapsed && status !== 'running' }">
      <div class="tool-args">
        <label>参数:</label>
        <code>{{ displayArgs }}</code>
      </div>
      <div v-if="call.result" class="tool-result">
        <label>结果:</label>
        <code>{{ call.result }}</code>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Setting, Check, Close, ArrowRight } from '@element-plus/icons-vue'
import type { ToolCall } from '../types'

const props = defineProps<{
  call: ToolCall
  status: 'running' | 'done' | 'error'
  displayName?: string
  depth?: number
}>()

const collapsed = ref(true)

// 运行中时自动展开，完成后自动折叠
watch(
  () => props.status,
  (s) => {
    if (s === 'running') {
      collapsed.value = false
    } else {
      collapsed.value = true
    }
  }
)

const displayArgs = computed(() => {
  if (!props.call.arguments) return ''
  try {
    const parsed = JSON.parse(props.call.arguments)
    return JSON.stringify(parsed)
  } catch {
    return props.call.arguments
  }
})

const hasBody = computed(() => {
  return !!(props.call.arguments || props.call.result)
})

function toggle() {
  collapsed.value = !collapsed.value
}
</script>

<style scoped>
.tool-call-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  margin: var(--space-2) 0;
  background: var(--color-bg-page);
  transition: all 0.3s ease;
  font-size: var(--font-size-sm);
  max-width: 420px;
  overflow: hidden;
}

/* === 运行中状态 === */
.tool-call-card.status-running {
  border-color: var(--color-primary-border);
  background: var(--color-primary-light);
}

/* === 完成状态 === */
.tool-call-card.status-done {
  border-color: #4caf50;
  background: #e8f5e9;
}

/* === 错误状态 === */
.tool-call-card.status-error {
  border-color: #f44336;
  background: #ffebee;
}

/* === Header === */
.tool-call-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  cursor: pointer;
  user-select: none;
  transition: background-color var(--transition-fast);
}

.tool-call-header:hover {
  background-color: rgba(0, 0, 0, 0.04);
}

/* === Chevron === */
.tool-chevron {
  font-size: 14px;
  transition: transform 0.25s ease;
  flex-shrink: 0;
  color: var(--color-text-tertiary);
}

.tool-chevron.expanded {
  transform: rotate(90deg);
}

.tool-status-icon {
  display: flex;
  align-items: center;
  font-size: 14px;
  flex-shrink: 0;
}

.status-running .tool-status-icon {
  color: var(--color-primary);
}

.status-done .tool-status-icon {
  color: #4caf50;
}

.status-error .tool-status-icon {
  color: #f44336;
}

.tool-name {
  font-weight: var(--font-weight-semibold);
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  flex: 1;
}

.tool-status-text {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.tool-collapse-badge {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background-color: rgba(0, 0, 0, 0.06);
  font-style: italic;
  flex-shrink: 0;
}

.depth-badge {
  font-size: 10px;
  padding: 0 4px;
  height: 18px;
  line-height: 18px;
  flex-shrink: 0;
}

/* === Body === */
.tool-call-body {
  overflow: hidden;
  transition: max-height 0.3s ease, padding 0.3s ease, border 0.3s ease;
  max-height: 20000px;
  padding: 0 var(--space-3) var(--space-2);
  border-top: 1px solid rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.tool-call-body.collapsed {
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
  border-top-color: transparent;
}

.tool-args,
.tool-result {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-top: var(--space-2);
}

.tool-args label,
.tool-result label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  font-weight: var(--font-weight-medium);
}

.tool-args code,
.tool-result code {
  font-size: var(--font-size-xs);
  font-family: var(--font-mono);
  color: var(--color-text-secondary);
  background: rgba(0, 0, 0, 0.04);
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  word-break: break-all;
  white-space: pre-wrap;
  line-height: var(--line-height-relaxed);
}

/* === 旋转动画 === */
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.spinning {
  animation: spin 1s linear infinite;
}
</style>
