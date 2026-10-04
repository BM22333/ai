<template>
  <div class="system-prompt-editor-page">
    <!-- 顶部工具栏 -->
    <header class="editor-header">
      <div class="header-left">
        <el-button text @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <div class="header-title">
          <el-icon :size="18"><Edit /></el-icon>
          <h1>系统提示词编辑</h1>
        </div>
      </div>
      <div class="header-right">
        <el-button @click="resetPrompt" :disabled="!hasChanges">
          <el-icon><RefreshLeft /></el-icon>
          重置
        </el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">
          <el-icon><Check /></el-icon>
          保存
        </el-button>
      </div>
    </header>

    <!-- 编辑区域 -->
    <main class="editor-main">
      <div class="editor-info">
        <div class="info-item">
          <span class="info-label">会话名称</span>
          <span class="info-value">{{ conversationName }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">字符数</span>
          <span class="info-value">{{ promptLength }}</span>
        </div>
      </div>

      <div class="editor-wrapper">
        <el-input
          v-model="editingPrompt"
          type="textarea"
          :rows="20"
          placeholder="输入系统提示词，用于设定AI的行为、角色和回复风格..."
          class="prompt-editor"
          :autosize="false"
          resize="none"
        />
      </div>

      <div class="editor-help">
        <el-collapse>
          <el-collapse-item title="💡 什么是系统提示词？" name="1">
            <p>
              系统提示词（System Prompt）用于设定 AI 的角色、行为准则和回复风格。
              它会在每次对话开始时发送给 AI，帮助 AI 更好地理解你的需求。
            </p>
            <p>例如：</p>
            <ul>
              <li>"你是一个专业的技术顾问，请用简洁清晰的语言回答技术问题。"</li>
              <li>"你是一位友好的英语老师，帮助我练习英语口语。"</li>
              <li>"请以JSON格式返回所有回答，不要包含额外的解释文字。"</li>
            </ul>
          </el-collapse-item>
        </el-collapse>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Edit, Check, RefreshLeft } from '@element-plus/icons-vue'
import { conversationApi } from '../api/conversation'

const router = useRouter()
const route = useRoute()

const conversationId = ref('')
const conversationName = ref('')
const originalPrompt = ref('')
const editingPrompt = ref('')
const saving = ref(false)

const promptLength = computed(() => {
  const len = editingPrompt.value.length
  return len === 0 ? '0' : len.toLocaleString()
})

const hasChanges = computed(() => {
  return editingPrompt.value !== originalPrompt.value
})

const goBack = async () => {
  if (hasChanges.value) {
    try {
      const { ElMessageBox } = await import('element-plus')
      await ElMessageBox.confirm(
        '你有未保存的修改，确定要离开吗？',
        '提示',
        {
          confirmButtonText: '确定离开',
          cancelButtonText: '继续编辑',
          type: 'warning'
        }
      )
    } catch {
      return
    }
  }
  router.back()
}

const resetPrompt = () => {
  editingPrompt.value = originalPrompt.value
}

const handleSave = async () => {
  if (!editingPrompt.value.trim()) {
    ElMessage.warning('系统提示词不能为空')
    return
  }

  saving.value = true
  try {
    const response = await conversationApi.updateConversation({
      conversationId: conversationId.value,
      systemPrompt: editingPrompt.value
    })

    if (response.code === 1) {
      originalPrompt.value = editingPrompt.value
      ElMessage.success('系统提示词已保存')
      router.back()
    } else {
      ElMessage.error(response.msg || '保存失败')
    }
  } catch (error: any) {
    console.error('保存系统提示词失败:', error)
    ElMessage.error(error?.msg || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  const id = route.params.conversationId as string
  if (!id) {
    ElMessage.error('缺少会话ID参数')
    router.back()
    return
  }

  conversationId.value = id

  // 从会话列表加载系统提示词
  try {
    const userId = localStorage.getItem('userId') || 'default_user'
    const response = await conversationApi.getAllConversations(userId)

    if (response.code === 1 && response.data) {
      const conv = response.data.find((c: any) => c.conversationId === id)
      if (conv) {
        conversationName.value = conv.conversationName || '未命名会话'
        originalPrompt.value = conv.systemPrompt || ''
        editingPrompt.value = originalPrompt.value
      } else {
        ElMessage.error('未找到该会话')
        router.back()
      }
    } else {
      ElMessage.error('加载会话信息失败')
      router.back()
    }
  } catch (error) {
    console.error('加载会话信息失败:', error)
    ElMessage.error('加载会话信息失败')
    router.back()
  }
})
</script>

<style scoped>
.system-prompt-editor-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: var(--color-bg-surface);
}

/* ===== Header ===== */
.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-6);
  border-bottom: 1px solid var(--color-border-light);
  background-color: var(--color-bg-page);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.header-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-primary);
}

.header-title h1 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.header-right {
  display: flex;
  gap: var(--space-2);
}

/* ===== Main ===== */
.editor-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: var(--space-6);
  max-width: 960px;
  width: 100%;
  margin: 0 auto;
}

/* ===== Info bar ===== */
.editor-info {
  display: flex;
  align-items: center;
  gap: var(--space-6);
  margin-bottom: var(--space-4);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--color-border-light);
}

.info-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.info-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  font-weight: var(--font-weight-medium);
}

.info-value {
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  font-weight: var(--font-weight-medium);
}

/* ===== Editor ===== */
.editor-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-bottom: var(--space-4);
  min-height: 0;
}

.prompt-editor {
  flex: 1;
  height: 100%;
}

.prompt-editor :deep(.el-textarea) {
  height: 100%;
}

.prompt-editor :deep(.el-textarea__inner) {
  height: 100% !important;
  min-height: 400px;
  resize: none;
  border-radius: var(--radius-lg);
  font-size: var(--font-size-base);
  line-height: var(--line-height-relaxed);
  padding: var(--space-5);
  background-color: var(--color-bg-page);
  border: 1px solid var(--color-border);
  font-family: var(--font-mono, 'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace);
  tab-size: 2;
  transition: border-color var(--transition-fast),
              box-shadow var(--transition-fast);
}

.prompt-editor :deep(.el-textarea__inner):focus {
  border-color: var(--color-primary-border);
  box-shadow: 0 0 0 3px var(--color-primary-border);
  background-color: #fff;
}

.prompt-editor :deep(.el-textarea__inner)::placeholder {
  color: var(--color-text-tertiary);
  font-family: inherit;
}

/* ===== Help section ===== */
.editor-help {
  flex-shrink: 0;
}

.editor-help :deep(.el-collapse) {
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  background-color: var(--color-bg-page);
}

.editor-help :deep(.el-collapse-item__header) {
  padding: var(--space-2) var(--space-4);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  border: none;
}

.editor-help :deep(.el-collapse-item__wrap) {
  border: none;
}

.editor-help :deep(.el-collapse-item__content) {
  padding: var(--space-2) var(--space-4) var(--space-4);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: var(--line-height-relaxed);
}

.editor-help p {
  margin: 0 0 var(--space-2) 0;
}

.editor-help ul {
  margin: 0;
  padding-left: var(--space-5);
}

.editor-help li {
  margin-bottom: var(--space-1);
}
</style>
