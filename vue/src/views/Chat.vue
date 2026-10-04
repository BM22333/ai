<template>
  <div class="chat-container">
    <el-container>
      <el-aside width="280px" class="sidebar">
        <div class="sidebar-header">
          <h3>会话列表</h3>
          <div class="sidebar-header-actions">
            <el-button type="primary" size="small" :disabled="isTyping" @click="openCreateDialog">
              <el-icon><Plus /></el-icon>
              新建会话
            </el-button>
            <el-button size="small" @click="managementDialogRef?.open()">
              <el-icon><Setting /></el-icon>
              管理
            </el-button>
          </div>
        </div>
        
        <div class="conversation-list">
          <div v-if="loadingConversations" class="loading-state">
            <el-icon class="is-loading"><ChatDotRound /></el-icon>
            <span>加载中...</span>
          </div>
          <div v-else-if="conversations.length === 0" class="empty-conversations">
            <p>暂无会话，点击"新建会话"开始</p>
          </div>
          <div
            v-for="conv in conversations"
            :key="conv.conversationId"
            class="conversation-item"
            :class="{ active: currentConversationId === conv.conversationId, disabled: isTyping }"
            @click="!isTyping && selectConversation(conv.conversationId)"
          >
            <div class="conversation-info">
              <el-icon><ChatDotRound /></el-icon>
              <span class="conversation-title">{{ conv.conversationName || '新会话' }}</span>
            </div>
            <div class="conversation-actions">
              <el-button
                type="primary"
                size="small"
                text
                :disabled="isTyping"
                @click.stop="openConversationSettings(conv)"
              >
                <el-icon><Setting /></el-icon>
              </el-button>
              <el-button
                type="danger"
                size="small"
                text
                :disabled="isTyping"
                @click.stop="deleteConversation(conv.conversationId)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
        
        <div class="user-info">
          <el-dropdown>
            <span class="user-name">
              <el-icon><User /></el-icon>
              {{ username }}
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
                <el-dropdown-item @click="handleDeactivate" class="deactivate-item">
                  <el-icon><CircleCloseFilled /></el-icon>
                  注销账号
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-aside>

      <el-main class="chat-main">
        <div v-if="!currentConversationId" class="empty-state">
          <el-icon :size="80" color="#909399"><ChatDotRound /></el-icon>
          <h2>欢迎使用！</h2>
          <p>点击"新建会话"开始与AI对话</p>
        </div>

        <div v-else class="chat-content">
          <!-- 工具指示器 & 会话设置 -->
          <div class="chat-toolbar">
            <div class="tool-indicators">
              <span v-if="conversationTools.length > 0" class="tool-badge default-tools">
                <el-icon :size="14"><Switch /></el-icon>
                会话工具：
                <el-tag
                  v-for="t in conversationTools"
                  :key="t"
                  size="small"
                  type="primary"
                  effect="light"
                  class="tool-tag"
                >{{ toolDisplayName(t) }}</el-tag>
              </span>
              <span v-if="tempToolNames.length > 0" class="tool-badge extra-tools">
                本次追加：
                <el-tag
                  v-for="t in tempToolNames"
                  :key="t"
                  size="small"
                  type="success"
                  effect="light"
                  class="tool-tag"
                >{{ toolDisplayName(t) }}</el-tag>
              </span>
              <span v-if="enableToolsOverride === false" class="tool-badge disabled-tools">
                <el-tag size="small" type="danger" effect="light">工具已禁用</el-tag>
              </span>
              <span v-if="enableToolsOverride === true" class="tool-badge all-tools">
                <el-tag size="small" type="warning" effect="light">全部工具已启用</el-tag>
              </span>
              <span v-if="isDeepThinking" class="tool-badge deep-thinking-indicator">
                <el-tag size="small" class="deep-thinking-tag" effect="light">
                  <el-icon :size="12"><Star /></el-icon>
                  深度思考
                </el-tag>
              </span>
            </div>
            <el-button text size="small" @click="openCurrentConversationSettings" :disabled="isTyping">
              <el-icon><Setting /></el-icon>
              会话设置
            </el-button>
          </div>

          <div class="messages-container" ref="messagesContainer">
            <!-- 无消息时的引导提示 -->
            <div v-if="messages.length === 0 && !isTyping" class="messages-empty-hint">
              <el-icon :size="48" color="#909399"><ChatLineRound /></el-icon>
              <h3>开始对话</h3>
              <p>在下方输入你的第一条消息</p>
            </div>
            <div
              v-for="(message, index) in messages"
              :key="index"
              class="message-item"
              :class="[
                message.role.toLowerCase(),
                { 'message-hidden': message.isSeeable === false }
              ]"
            >
              <div class="message-avatar">
                <el-icon v-if="message.role === 'USER'" :size="24"><User /></el-icon>
                <el-icon v-else-if="message.role === 'TOOL'" :size="24"><Switch /></el-icon>
                <el-icon v-else :size="24"><ChatLineRound /></el-icon>
              </div>
              <div class="message-content">
                <!-- 编辑模式 -->
                <div v-if="editingMessageIndex === index" class="message-edit-mode" :class="message.role.toLowerCase()">
                  <!-- 思考过程文本框（仅 ASSISTANT 消息且包含 thinking 时显示） -->
                  <div v-if="editingThinkingContent !== null" class="edit-thinking-section">
                    <div class="edit-section-label">
                      <el-icon :size="14"><ChatLineRound /></el-icon>
                      <span>&lt;thinking&gt; 思考过程</span>
                    </div>
                    <el-input
                      v-model="editingThinkingContent"
                      type="textarea"
                      :rows="4"
                      placeholder="思考过程内容..."
                      class="edit-thinking-input"
                    />
                  </div>
                  <!-- 正文文本框 -->
                  <div class="edit-body-section">
                    <div class="edit-section-label">
                      <el-icon :size="14"><Edit /></el-icon>
                      <span>正文内容</span>
                    </div>
                    <el-input
                      v-model="editingBodyContent"
                      type="textarea"
                      :rows="4"
                      placeholder="编辑消息内容..."
                      class="edit-body-input"
                    />
                  </div>
                  <div v-if="messages.length - index - 1 > 0" class="edit-warning">
                    ⚠️ 保存后将清除此消息之后的 {{ messages.length - index - 1 }} 条后续消息
                  </div>
                  <div class="edit-actions">
                    <el-button size="small" type="primary" @click="saveEditMessage(index)">
                      保存
                    </el-button>
                    <el-button size="small" @click="cancelEdit">
                      取消
                    </el-button>
                  </div>
                </div>
                <!-- 正常模式 -->
                <div v-else class="message-bubble-wrapper">
                  <!-- TOOL 角色消息：紧凑工具结果卡片 -->
                  <div v-if="message.role === 'TOOL'" class="tool-message-compact" :class="{ collapsed: toolMsgCollapsed }">
                    <div class="tool-message-header" @click="toolMsgCollapsed = !toolMsgCollapsed">
                      <el-icon class="tool-msg-chevron" :class="{ expanded: !toolMsgCollapsed }">
                        <ArrowRight />
                      </el-icon>
                      <el-icon :size="14"><Switch /></el-icon>
                      <span class="tool-message-name">{{ message.toolName ? toolDisplayName(message.toolName) : '工具' }}</span>
                      <span class="tool-message-badge">工具返回</span>
                    </div>
                    <div class="tool-message-body" :class="{ collapsed: toolMsgCollapsed }">
                      <code>{{ message.content }}</code>
                    </div>
                    <div class="message-time">
                      {{ formatTime(message.timestamp) }}
                    </div>
                  </div>

                  <!-- 非 TOOL 消息的正常渲染 -->
                  <template v-else>
                    <!-- 工具调用卡片（仅 ASSISTANT 消息） -->
                    <div v-if="message.role === 'ASSISTANT' && message.toolCalls?.length" class="tool-calls-section">
                      <ToolCallCard
                        v-for="(tc, tcIdx) in message.toolCalls"
                        :key="tcIdx"
                        :call="tc"
                        :status="tc.result ? 'done' : 'running'"
                        :display-name="toolDisplayName(tc.functionName)"
                        :depth="message.depth"
                      />
                    </div>

                    <!-- 包含 thinking 标签的 ASSISTANT 消息：分段渲染 -->
                    <template v-if="getMessageSegments(message, index)">
                      <div class="message-text">
                        <template v-for="(seg, segIdx) in getMessageSegments(message, index)" :key="segIdx">
                          <ThinkingBlock
                            v-if="seg.type === 'thinking'"
                            :content="seg.content"
                            :is-streaming="isLastAssistantMessage(index)"
                            :is-complete="seg.isComplete ?? false"
                          />
                          <div v-else class="message-text-segment" v-html="renderContent(seg.content)"></div>
                        </template>
                      </div>
                    </template>
                    <!-- 普通消息（无 thinking 标签） -->
                    <div v-else class="message-text" v-html="renderContent(message.content)"></div>

                    <div class="message-time">
                      {{ formatTime(message.timestamp) }}
                      <el-button
                        v-if="index < messages.length - 1"
                        type="primary"
                        size="small"
                        text
                        :disabled="isTyping"
                        @click="handleRollback(message)"
                      >
                        回溯到此处
                      </el-button>
                    </div>
                    <div class="message-actions">
                      <el-button
                        size="small"
                        text
                        :disabled="isTyping"
                        @click="startEditMessage(index, message)"
                        title="编辑此消息"
                      >
                        <el-icon :size="15"><Edit /></el-icon>
                      </el-button>
                      <el-button
                        size="small"
                        text
                        type="danger"
                        :disabled="isTyping"
                        @click="handleDeleteMessage(index, message)"
                        title="删除此消息"
                      >
                        <el-icon :size="15"><Delete /></el-icon>
                      </el-button>
                      <el-button
                        v-if="message.isSeeable !== false"
                        size="small"
                        text
                        type="warning"
                        :disabled="isTyping"
                        @click="hideSingleMessage(index)"
                        title="隐藏此消息（AI 将看不到）"
                      >
                        <el-icon :size="15"><Hide /></el-icon>
                      </el-button>
                      <el-button
                        v-if="message.isSeeable === false"
                        size="small"
                        text
                        type="success"
                        :disabled="isTyping"
                        @click="restoreSingleMessage(index)"
                        title="恢复显示（AI 重新可见）"
                      >
                        <el-icon :size="15"><View /></el-icon>
                      </el-button>
                    </div>
                  </template>
                  <!-- 隐藏消息角标 -->
                  <div v-if="message.isSeeable === false" class="hidden-indicator" title="此消息已对 AI 隐藏">
                    <el-icon :size="14"><Hide /></el-icon>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="isTyping" class="message-item assistant">
              <div class="message-avatar">
                <el-icon :size="24"><ChatLineRound /></el-icon>
              </div>
              <div class="message-content">
                <div class="typing-indicator">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
              </div>
            </div>
          </div>
          
          <div class="input-container">
            <!-- 功能选择器 -->
            <el-popover
              placement="top-start"
              :width="280"
              trigger="click"
              :disabled="isTyping"
            >
              <template #reference>
                <el-button text :disabled="isTyping" class="tool-selector-btn">
                  <el-icon><Operation /></el-icon>
                  功能
                  <el-badge
                    v-if="selectedInsertPromptIds.length > 0"
                    :value="selectedInsertPromptIds.length"
                    class="func-badge"
                    type="primary"
                  />
                </el-button>
              </template>
              <div class="func-selector-content">
                <el-button
                  text
                  @click="toggleDeepThinking"
                  class="func-item-btn"
                  :class="{ 'func-item-active': isDeepThinking }"
                >
                  <el-icon><Star /></el-icon>
                  深度思考
                  <el-icon v-if="isDeepThinking" class="deep-thinking-check"><Check /></el-icon>
                </el-button>
                <el-button
                  v-if="messages.length > 0"
                  text
                  :disabled="isTyping"
                  @click="handleRegenerate"
                  class="func-item-btn"
                >
                  <el-icon><Refresh /></el-icon>
                  重新回复
                </el-button>

                <!-- 注入提示词 -->
                <el-button
                  text
                  :disabled="isTyping"
                  @click="insertPromptSelectorRef?.open(selectedInsertPromptIds)"
                  class="func-item-btn"
                >
                  <el-icon><DocumentAdd /></el-icon>
                  注入提示词
                  <el-badge
                    v-if="selectedInsertPromptIds.length > 0"
                    :value="selectedInsertPromptIds.length"
                    class="func-badge func-badge-right"
                    type="primary"
                  />
                </el-button>

                <!-- 批量隐藏 -->
                <el-button
                  text
                  :disabled="isTyping || messages.length === 0"
                  @click="batchHideDialogRef?.open(currentConversationId, messages)"
                  class="func-item-btn"
                >
                  <el-icon><Hide /></el-icon>
                  批量隐藏
                </el-button>
              </div>
            </el-popover>

            <!-- 工具选择器 -->
            <el-popover
              placement="top-start"
              :width="380"
              trigger="click"
              :disabled="isTyping"
              @show="refreshMcpStatus"
            >
              <template #reference>
                <el-button text :disabled="isTyping" class="tool-selector-btn">
                  <el-icon><Switch /></el-icon>
                  工具
                </el-button>
              </template>
              <div class="tool-selector-content">
                <div class="tool-section">
                  <div class="tool-section-title">会话默认工具（始终可用，除非禁用）:</div>
                  <div v-if="conversationTools.length === 0" class="no-default-tools">无</div>
                  <el-tag
                    v-for="t in conversationTools"
                    :key="t"
                    size="small"
                    type="primary"
                    effect="light"
                    class="tool-tag"
                  >{{ toolDisplayName(t) }}</el-tag>
                </div>

                <div class="tool-section">
                  <div class="tool-section-title">本次临时追加:</div>
                  <div v-if="flatAvailableTools.length === 0" class="no-default-tools">暂无可用的工具</div>
                  <template v-else>
                    <!-- 内置工具 -->
                    <div v-if="filteredBuiltinTools.length > 0" class="tool-subsection">
                      <div class="tool-subsection-title">内置工具</div>
                      <el-checkbox-group v-model="tempToolNames" class="tool-checkbox-list">
                        <el-tooltip
                          v-for="tool in filteredBuiltinTools"
                          :key="tool.name"
                          :content="tool.description"
                          placement="top-start"
                          :show-after="500"
                          effect="light"
                          :popper-style="{ maxWidth: '360px' }"
                        >
                          <el-checkbox :label="tool.name" :value="tool.name">
                            <span class="tool-checkbox-name">{{ tool.name }}</span>
                            <span class="tool-checkbox-desc">— {{ tool.description }}</span>
                          </el-checkbox>
                        </el-tooltip>
                      </el-checkbox-group>
                    </div>
                    <!-- MCP 工具（按 Server 分组） -->
                    <div
                      v-for="group in mcpToolGroups"
                      :key="group.serverName"
                      class="tool-subsection"
                    >
                      <div class="tool-subsection-title mcp-subsection-title">
                        <span>MCP: {{ group.serverName }}</span>
                        <el-tag
                          :type="group.connected ? 'success' : 'danger'"
                          size="small"
                          effect="light"
                        >
                          {{ group.connected ? '已连接' : '已断开' }}
                        </el-tag>
                      </div>
                      <el-checkbox-group v-model="tempToolNames" class="tool-checkbox-list">
                        <el-tooltip
                          v-for="tool in group.tools.filter(t => !conversationTools.includes(t.fullName))"
                          :key="tool.fullName"
                          :content="tool.description"
                          placement="top-start"
                          :show-after="500"
                          effect="light"
                          :popper-style="{ maxWidth: '360px' }"
                        >
                          <el-checkbox :label="tool.fullName" :value="tool.fullName">
                            <span class="tool-checkbox-name">{{ tool.displayName }}</span>
                            <span class="tool-checkbox-desc">— {{ tool.description }}</span>
                          </el-checkbox>
                        </el-tooltip>
                      </el-checkbox-group>
                    </div>
                  </template>
                </div>

                <el-divider style="margin: var(--space-3) 0;" />

                <div class="tool-mode-section">
                  <div class="tool-section-title">工具模式:</div>
                  <el-radio-group v-model="toolsMode" size="small">
                    <el-radio value="merge" style="display: block; margin-bottom: var(--space-2);">
                      使用当前配置（默认 + 追加）
                    </el-radio>
                    <el-radio value="all" style="display: block; margin-bottom: var(--space-2);">
                      启用全部工具
                    </el-radio>
                    <el-radio value="none" style="display: block;">
                      本次不使用工具
                    </el-radio>
                  </el-radio-group>
                </div>
              </div>
            </el-popover>

            <el-input
              v-model="inputMessage"
              type="textarea"
              :rows="3"
              placeholder="输入消息..."
              @keydown.enter.prevent="handleSend"
            />
            <el-button
              v-if="isTyping"
              type="danger"
              @click="handleStopReply"
            >
              <el-icon><Close /></el-icon>
              停止
            </el-button>
            <el-button
              v-else
              type="primary"
              @click="handleSend"
              :disabled="!inputMessage.trim()"
            >
              发送
            </el-button>
          </div>
        </div>
      </el-main>
    </el-container>
    
    <CreateConversationDialog ref="createDialogRef" @created="handleConversationCreated" />
    <ConversationSettingsDialog ref="settingsDialogRef" @updated="handleConversationUpdated" />
    <ManagementDialog ref="managementDialogRef" />
    <BatchHideDialog ref="batchHideDialogRef" @done="handleBatchHideDone" />
    <InsertPromptSelectorDialog ref="insertPromptSelectorRef" @confirm="handleInsertPromptsConfirm" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  User,
  ChatLineRound,
  Plus,
  Delete,
  SwitchButton,
  Setting,
  Switch,
  Edit,
  Refresh,
  Operation,
  Close,
  Star,
  Check,
  CircleCloseFilled,
  ArrowRight,
  Hide,
  View,
  DocumentAdd
} from '@element-plus/icons-vue'
import { conversationApi, chatApi } from '../api/conversation'
import { mcpApi, getDisplayToolName, isMcpTool } from '../api/mcp'
import type { Message as MessageType, ToolInfo, McpServerGroup } from '../types'
import { parseThinking, hasThinking } from '../utils/thinking'
import type { ParsedSegment } from '../utils/thinking'
import CreateConversationDialog from '../components/CreateConversationDialog.vue'
import ConversationSettingsDialog from '../components/ConversationSettingsDialog.vue'
import ThinkingBlock from '../components/ThinkingBlock.vue'
import ToolCallCard from '../components/ToolCallCard.vue'
import ManagementDialog from '../components/ManagementDialog.vue'
import BatchHideDialog from '../components/BatchHideDialog.vue'
import InsertPromptSelectorDialog from '../components/InsertPromptSelectorDialog.vue'

const router = useRouter()
const createDialogRef = ref()
const settingsDialogRef = ref()
const messagesContainer = ref<HTMLElement>()

const username = ref(localStorage.getItem('username') || '用户')
const conversations = ref<any[]>([])
const currentConversationId = ref<string>('')
const messages = ref<MessageType[]>([])
const inputMessage = ref('')
const isTyping = ref(false)
const isDeepThinking = ref(true)
const abortController = ref<AbortController | null>(null)
const loadingConversations = ref(false)

// === 工具调用状态 ===
const availableTools = ref<ToolInfo[]>([])
const conversationTools = ref<string[]>([])
const tempToolNames = ref<string[]>([])
const toolsMode = ref<'merge' | 'all' | 'none'>('merge')
const knownMcpServers = ref<{ name: string }[]>([])

// 派生的工具覆盖状态（用于模板中 v-if 判断）
const enableToolsOverride = computed<boolean | undefined>(() => {
  if (toolsMode.value === 'none') return false
  if (toolsMode.value === 'all') return true
  return undefined // 'merge' mode
})

// === 消息编辑状态 ===
const editingMessageIndex = ref<number | null>(null)
const editingThinkingContent = ref<string | null>(null)
const editingBodyContent = ref('')

// === 工具消息折叠状态 ===
const toolMsgCollapsed = ref(true)

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')

    ElMessage.success('已退出登录')
    router.push('/login')
  } catch {

  }
}

/** 注销用户：调用后端注销接口，清除本地状态，跳转登录页。此操作不可恢复。 */
const handleDeactivate = async () => {
  try {
    await ElMessageBox.confirm(
      '注销后您的账号将被禁用，所有数据将被清除且不可恢复。\n\n确定要继续吗？',
      '注销账号',
      {
        confirmButtonText: '确认注销',
        cancelButtonText: '取消',
        type: 'error',
        confirmButtonClass: 'el-button--danger'
      }
    )

    const userId = localStorage.getItem('userId') || ''
    const res = await mcpApi.deactivateUser({ userId })

    if (res.code === 1) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userId')
      localStorage.removeItem('username')

      ElMessage.success('账号已注销')
      router.push('/login')
    } else {
      ElMessage.error(res.msg || '注销失败')
    }
  } catch {
    // 用户取消操作
  }
}

const openCreateDialog = () => {
  if (isTyping.value) {
    ElMessage.warning('AI正在回复中，请等待回复完成后再创建新会话')
    return
  }
  createDialogRef.value?.open()
}

const handleConversationCreated = async (conversationId: string) => {
  await loadConversations()
  await selectConversation(conversationId)
}

const selectConversation = async (conversationId: string) => {
  console.log('选择会话，conversationId:', conversationId)
  currentConversationId.value = conversationId
  messages.value = []
  tempToolNames.value = []
  toolsMode.value = 'merge'
  toolMsgCollapsed.value = true
  segmentsCache.clear()
  selectedInsertPromptIds.value = []

  try {
    console.log('开始连接会话...')
    const response = await conversationApi.connectConversation(conversationId)
    console.log('连接会话响应:', response)

    if (response.code === 1) {
      // 加载该会话的工具偏好
      const conv = conversations.value.find(c => c.conversationId === conversationId)
      if (conv?.toolPreferences) {
        try {
          conversationTools.value = typeof conv.toolPreferences === 'string'
            ? JSON.parse(conv.toolPreferences)
            : conv.toolPreferences
        } catch {
          conversationTools.value = []
        }
      } else {
        conversationTools.value = []
      }

      await loadHistory(conversationId)
    } else {
      console.error('连接会话失败，响应:', response)
      ElMessage.error(response.msg || '连接会话失败')
    }
  } catch (error: any) {
    console.error('连接会话失败:', error)
    if (error.response) {
      console.error('错误响应状态:', error.response.status)
      console.error('错误响应数据:', error.response.data)
    }
    ElMessage.error(error.msg || '连接会话失败')
  }
}

const loadHistory = async (conversationId: string) => {
  try {
    console.log('开始加载历史记录，conversationId:', conversationId)
    const response = await conversationApi.getHistory(conversationId)
    console.log('历史记录响应:', response)
    
    if (response.code === 1) {
      if (response.data && response.data.length > 0) {
        console.log('历史记录数据:', response.data)
        messages.value = response.data
        scrollToBottom()
      } else {
        console.log('该会话没有历史记录')
        messages.value = []
      }
    } else {
      console.error('加载历史记录失败，响应:', response)
      ElMessage.error(response.msg || '加载历史记录失败')
    }
  } catch (error: any) {
    console.error('加载历史记录失败:', error)
    if (error.response) {
      console.error('错误响应状态:', error.response.status)
      console.error('错误响应数据:', error.response.data)
    }
    ElMessage.error(error.msg || '加载历史记录失败')
  }
}

const deleteConversation = async (conversationId: string) => {
  if (isTyping.value) {
    ElMessage.warning('AI正在回复中，请等待回复完成后再删除会话')
    return
  }
  
  try {
    await ElMessageBox.confirm('确定要删除这个会话吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const response = await conversationApi.deleteConversation(conversationId)
    
    if (response.code === 1) {
      ElMessage.success('删除成功')
      conversations.value = conversations.value.filter(c => c.conversationId !== conversationId)
      
      if (currentConversationId.value === conversationId) {
        currentConversationId.value = ''
        messages.value = []
      }
    } else {
      ElMessage.error(response.msg || '删除失败')
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.msg || '删除失败')
    }
  }
}

const handleRollback = async (message: MessageType) => {
  if (isTyping.value) {
    ElMessage.warning('AI正在回复中，请等待回复完成后再进行回溯操作')
    return
  }
  
  try {
    await ElMessageBox.confirm(
      `确定要回溯到这条消息吗？此操作将删除该消息及之后的所有对话，且不可恢复。`,
      '确认回溯',
      {
        confirmButtonText: '确定回溯',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    let timestamp: number
    if (typeof message.timestamp === 'string') {
      timestamp = new Date(message.timestamp).getTime()
    } else if (message.timestamp instanceof Date) {
      timestamp = message.timestamp.getTime()
    } else {
      timestamp = message.timestamp as number
    }
    
    timestamp = timestamp - 1
    
    console.log('回溯到消息:', message.content)
    console.log('使用timestamp:', timestamp)
    console.log('执行回溯操作，conversationId:', currentConversationId.value)
    
    const response = await conversationApi.rollbackConversation({
      conversationId: currentConversationId.value,
      timestamp
    })
    
    if (response.code === 1) {
      ElMessage.success(response.msg || '回溯成功')
      await loadHistory(currentConversationId.value)
    } else {
      ElMessage.error(response.msg || '回溯失败')
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('回溯失败:', error)
      ElMessage.error(error.msg || '回溯失败')
    }
  }
}

const startEditMessage = (index: number, message: MessageType) => {
  editingMessageIndex.value = index

  // 解析消息内容，分离 thinking 和正文
  if (message.role === 'ASSISTANT' && hasThinking(message.content)) {
    const segments = parseThinking(message.content)
    const thinkingParts: string[] = []
    const bodyParts: string[] = []

    for (const seg of segments) {
      if (seg.type === 'thinking') {
        thinkingParts.push(seg.content)
      } else {
        bodyParts.push(seg.content)
      }
    }

    editingThinkingContent.value = thinkingParts.join('\n')
    editingBodyContent.value = bodyParts.join('')
  } else {
    editingThinkingContent.value = null
    editingBodyContent.value = message.content || ''
  }
}

const cancelEdit = () => {
  editingMessageIndex.value = null
  editingThinkingContent.value = null
  editingBodyContent.value = ''
}

const saveEditMessage = async (index: number) => {
  // 组合 thinking 和正文内容
  let newContent: string
  if (editingThinkingContent.value !== null && editingThinkingContent.value.trim()) {
    newContent = `<thinking>${editingThinkingContent.value.trim()}</thinking>\n${editingBodyContent.value.trim()}`
  } else {
    newContent = editingBodyContent.value.trim()
  }

  if (!newContent.trim()) {
    ElMessage.warning('消息内容不能为空')
    return
  }

  if (!currentConversationId.value) return

  try {
    const response = await conversationApi.editMessage({
      conversationId: currentConversationId.value,
      messageIndex: index,
      newContent
    })

    if (response.code === 1) {
      ElMessage.success(response.msg || '消息编辑成功')
      editingMessageIndex.value = null
      editingThinkingContent.value = null
      editingBodyContent.value = ''
      // 重新加载历史（后续消息已被截断）
      await loadHistory(currentConversationId.value)
    } else {
      ElMessage.error(response.msg || '编辑失败')
    }
  } catch (error: any) {
    console.error('编辑消息失败:', error)
    ElMessage.error(error?.msg || '编辑失败')
  }
}

const handleDeleteMessage = async (index: number, message: MessageType) => {
  if (isTyping.value) {
    ElMessage.warning('AI正在回复中，请等待回复完成后再删除消息')
    return
  }

  if (!currentConversationId.value) return

  try {
    const contentPreview = message.content.length > 50
      ? message.content.substring(0, 50) + '...'
      : message.content

    await ElMessageBox.confirm(
      `确定要删除这条消息吗？\n\n将删除的消息：\n  ${message.role}: "${contentPreview}"\n\n删除后其他消息不受影响。`,
      '确认删除',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const response = await conversationApi.deleteMessage({
      conversationId: currentConversationId.value,
      messageIndex: index
    })

    if (response.code === 1) {
      ElMessage.success('消息已删除')
      // 重新加载历史（消息索引前移）
      await loadHistory(currentConversationId.value)
    } else {
      ElMessage.error(response.msg || '删除失败')
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除消息失败:', error)
      ElMessage.error(error?.msg || '删除失败')
    }
  }
}

// === 消息隐藏 / 恢复 ===

const hideSingleMessage = async (index: number) => {
  if (!currentConversationId.value) return
  const message = messages.value[index]
  if (message.role === 'TOOL') {
    try {
      await ElMessageBox.confirm(
        '隐藏工具消息时，与该工具调用配对的 ASSISTANT(tool_calls) 消息也会被一同隐藏。确定要继续吗？',
        '级联影响警告',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
  }
  const res = await conversationApi.hideMessages({
    conversationId: currentConversationId.value,
    messageIndexes: [index]
  })
  if (res.code === 1) {
    await loadHistory(currentConversationId.value)
  } else {
    ElMessage.error(res.msg || '隐藏失败')
  }
}

const restoreSingleMessage = async (index: number) => {
  if (!currentConversationId.value) return
  const res = await conversationApi.showMessages({
    conversationId: currentConversationId.value,
    messageIndexes: [index]
  })
  if (res.code === 1) {
    await loadHistory(currentConversationId.value)
  } else {
    ElMessage.error(res.msg || '恢复失败')
  }
}

// === 批量隐藏（委托给 BatchHideDialog） ===

const handleBatchHideDone = async () => {
  await loadHistory(currentConversationId.value)
}

const handleInsertPromptsConfirm = (ids: number[]) => {
  selectedInsertPromptIds.value = ids
}

const handleSend = async () => {
  if (!inputMessage.value.trim() || !currentConversationId.value) return

  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''

  console.log('发送用户消息:', userMessage)
  console.log('当前会话ID:', currentConversationId.value)

  messages.value.push({
    role: 'USER',
    content: userMessage,
    timestamp: new Date()
  })

  scrollToBottom()
  await streamAiResponse(userMessage)
}

/**
 * 重新回复：移除最后一条 USER 及之后的 ASSISTANT 消息，重新生成回复。
 */
const handleRegenerate = async () => {
  if (isTyping.value || !currentConversationId.value) return
  if (messages.value.length === 0) return

  // 找到最后一条 USER 消息
  let lastUserIndex = -1
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i].role === 'USER') {
      lastUserIndex = i
      break
    }
  }
  if (lastUserIndex === -1) return

  const lastUserMessage = messages.value[lastUserIndex].content

  // 获取用户消息的时间戳，用于后端回滚
  let timestamp: number
  const msgTimestamp = messages.value[lastUserIndex].timestamp
  if (typeof msgTimestamp === 'string') {
    timestamp = new Date(msgTimestamp).getTime()
  } else if (msgTimestamp instanceof Date) {
    timestamp = msgTimestamp.getTime()
  } else {
    timestamp = msgTimestamp as number
  }

  // 先从后端回滚到这条用户消息之前，避免后端产生重复记录
  try {
    await conversationApi.rollbackConversation({
      conversationId: currentConversationId.value,
      timestamp: timestamp - 1
    })
  } catch (error) {
    console.error('重新回复：回滚失败', error)
  }

  // 从本地列表中删除该用户消息及之后的所有消息
  messages.value.splice(lastUserIndex)

  // 重新添加用户消息（与正常发送流程一致）
  messages.value.push({
    role: 'USER',
    content: lastUserMessage,
    timestamp: new Date()
  })

  segmentsCache.clear()
  scrollToBottom()
  await streamAiResponse(lastUserMessage)
}

/**
 * 停止回复：取消当前正在进行的 AI 流式响应。
 */
const handleStopReply = () => {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
}

/**
 * 切换深度思考模式。
 */
const toggleDeepThinking = () => {
  isDeepThinking.value = !isDeepThinking.value
}

/**
 * 核心流式响应处理：调用 API 并将流式内容写入 messages。
 */
const streamAiResponse = async (userMessage: string) => {
  isTyping.value = true

  // 创建新的 AbortController 用于取消请求
  const controller = new AbortController()
  abortController.value = controller

  let streamCompleted = false
  let assistantMessage = ''
  let inThinking = false
  let pendingToolCalls: any[] = []

  try {
    const userId = localStorage.getItem('userId') || 'default_user'

    const chatData: any = {
      message: userMessage,
      userId,
      conversationId: currentConversationId.value
    }

    if (toolsMode.value === 'none') {
      chatData.enableTools = false
    } else if (toolsMode.value === 'all') {
      chatData.enableTools = true
    } else if (tempToolNames.value.length > 0) {
      chatData.toolNames = tempToolNames.value
    }

    if (selectedInsertPromptIds.value.length > 0) {
      chatData.insertPromptIds = selectedInsertPromptIds.value
    }

    console.log('调用发送消息API，userId:', userId, 'conversationId:', currentConversationId.value, 'toolParams:', { toolNames: chatData.toolNames, enableTools: chatData.enableTools })
    const stream = await chatApi.sendMessage(chatData, controller.signal, isDeepThinking.value)

    if (!stream) {
      throw new Error('无法获取响应流')
    }

    console.log('获取到响应流')
    const reader = stream.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    messages.value.push({
      role: 'ASSISTANT',
      content: '',
      timestamp: new Date()
    })

    let chunkCount = 0
    while (true) {
      const { done, value } = await reader.read()

      if (done) {
        console.log('流式响应完成，总共接收', chunkCount, '个数据块')
        // 如果 thinking 块还未闭合，补上闭合标签
        if (inThinking) {
          assistantMessage += '</thinking>'
          inThinking = false
        }
        streamCompleted = true
        break
      }

      const chunk = decoder.decode(value, { stream: true })
      buffer += chunk
      chunkCount++

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmedLine = line.trim()
        if (trimmedLine.startsWith('data:')) {
          // 移除 "data:" 前缀，仅去除 SSE 规范中可选的前导空格
          let rawData = trimmedLine.substring(5)
          if (rawData.startsWith(' ')) {
            rawData = rawData.substring(1)
          }
          if (rawData) {
            try {
              // 新格式：{"type": "reasoning"|"content"|"tool_start"|"tool_end"|"error", ...}
              const parsed = JSON.parse(rawData)

              switch (parsed.type) {
                case 'reasoning':
                  if (parsed.text) {
                    if (!inThinking) {
                      assistantMessage += '<thinking>'
                      inThinking = true
                    }
                    assistantMessage += parsed.text
                  }
                  break

                case 'content':
                  if (parsed.text) {
                    if (inThinking) {
                      assistantMessage += '</thinking>'
                      inThinking = false
                    }
                    assistantMessage += parsed.text
                  }
                  break

                case 'tool_start': {
                  // 闭合未完成的 thinking 块
                  if (inThinking) {
                    assistantMessage += '</thinking>'
                    inThinking = false
                  }
                  // 创建新的工具调用记录
                  const lastGroup = pendingToolCalls[pendingToolCalls.length - 1]
                  if (!lastGroup || lastGroup.every((c: any) => c.result !== undefined)) {
                    pendingToolCalls.push([])
                  }
                  pendingToolCalls[pendingToolCalls.length - 1].push({
                    id: parsed.id || `call_stream_${Date.now()}`,
                    type: 'function',
                    functionName: parsed.tool || '',
                    arguments: parsed.args ? JSON.stringify(parsed.args) : ''
                  })
                  break
                }

                case 'tool_end': {
                  // 更新对应工具调用的结果
                  for (const group of pendingToolCalls) {
                    const call = group.find((c: any) =>
                      c.functionName === parsed.tool && c.result === undefined
                    )
                    if (call) {
                      call.result = parsed.result || ''
                      break
                    }
                  }
                  break
                }

                case 'error':
                  console.error('SSE error event:', parsed.message)
                  break
              }

            } catch {
              // 兼容旧格式：纯文本（可能包含字面 \n）
              assistantMessage += rawData.replace(/\\n/g, '\n')
            }

            const lastMessage = messages.value[messages.value.length - 1]
            lastMessage.content = assistantMessage
            // 将流式工具调用数据同步到消息对象，供模板渲染
            lastMessage.toolCalls = pendingToolCalls.flat()

            scrollToBottom()
          }
        }
      }
    }

  } catch (error: any) {
    // 用户主动取消请求，不报错
    if (error?.name === 'AbortError') {
      console.log('用户取消了回复')
      // 确保 thinking 块闭合
      if (inThinking) {
        assistantMessage += '</thinking>'
        inThinking = false
        const lastMessage = messages.value[messages.value.length - 1]
        if (lastMessage) {
          lastMessage.content = assistantMessage
        }
      }
    } else {
      console.error('发送消息失败:', error)
      if (error.response) {
        console.error('错误响应状态:', error.response.status)
        console.error('错误响应数据:', error.response.data)
      }
      ElMessage.error(error.message || '发送消息失败')

      messages.value.push({
        role: 'ASSISTANT',
        content: '抱歉，发生了错误，请重试。',
        timestamp: new Date()
      })
    }
  } finally {
    isTyping.value = false
    abortController.value = null
    scrollToBottom()

  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

/**
 * 对 HTML 特殊字符进行转义，防止 XSS。
 */
const escapeHtml = (text: string): string => {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/**
 * 将内容中的换行符转为 <br> 标签，用于 v-html 渲染。
 * 先转义 HTML，再将 \n 替换为 <br>。
 */
const renderContent = (text: string): string => {
  return escapeHtml(text).replace(/\n/g, '<br>')
}

const formatTime = (timestamp: Date) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

/**
 * 判断某条消息是否为最后一条 ASSISTANT 消息（正在流式输出中）。
 */
const isLastAssistantMessage = (index: number): boolean => {
  if (!isTyping.value) return false
  const lastIndex = messages.value.length - 1
  return index === lastIndex && messages.value[lastIndex]?.role === 'ASSISTANT'
}

/**
 * 解析消息内容。如果消息包含 <thinking> 标签则返回片段数组，否则返回 null。
 * 使用简单的缓存确保每次渲染周期内结果一致。
 */
const segmentsCache = new Map<string, ParsedSegment[] | null>()
const getMessageSegments = (message: MessageType, _index: number): ParsedSegment[] | null => {
  if (message.role !== 'ASSISTANT') return null
  if (!message.content) return null
  if (!hasThinking(message.content)) return null
  // 用内容作为缓存键，避免同一次渲染中重复解析
  const cached = segmentsCache.get(message.content)
  if (cached !== undefined) return cached
  const segments = parseThinking(message.content)
  segmentsCache.set(message.content, segments)
  return segments
}

const loadConversations = async () => {
  loadingConversations.value = true
  try {
    const userId = localStorage.getItem('userId') || 'default_user'
    console.log('开始加载会话列表，userId:', userId)
    const response = await conversationApi.getAllConversations(userId)
    
    if (response.code === 1 && response.data) {
      conversations.value = response.data
      console.log('会话列表加载成功:', conversations.value)
    } else {
      ElMessage.error(response.msg || '加载会话列表失败')
    }
  } catch (error: any) {
    console.error('加载会话列表失败:', error)
    if (error.response) {
      console.error('错误响应状态:', error.response.status)
      console.error('错误响应数据:', error.response.data)
    }
    if (error.message?.includes('No static resource')) {
      console.warn('后端接口 /ai/conversation/getAll 可能未正确配置')
      ElMessage.warning('会话列表功能暂时不可用')
    } else {
      ElMessage.error(error.msg || '加载会话列表失败')
    }
  } finally {
    loadingConversations.value = false
  }
}

// === 会话设置 ===
const openConversationSettings = (conv: any) => {
  settingsDialogRef.value?.open(conv)
}

const openCurrentConversationSettings = () => {
  const conv = conversations.value.find(c => c.conversationId === currentConversationId.value)
  if (conv) {
    settingsDialogRef.value?.open(conv)
  } else {
    ElMessage.warning('请先选择一个会话')
  }
}

const handleConversationUpdated = async (updatedData: any) => {
  // 刷新会话列表以获取最新数据
  await loadConversations()

  // 如果当前会话就是被更新的会话，刷新工具状态
  if (currentConversationId.value === updatedData.conversationId) {
    if (updatedData.toolPreferences) {
      try {
        conversationTools.value = typeof updatedData.toolPreferences === 'string'
          ? JSON.parse(updatedData.toolPreferences)
          : updatedData.toolPreferences
      } catch {
        conversationTools.value = []
      }
    }
  }
}

// === 加载可用工具列表 ===
const loadAvailableTools = async () => {
  const userId = localStorage.getItem('userId') || 'default_user'
  try {
    // 并行获取 BUILTIN 工具和 MCP 工具
    const [toolResponse, mcpResponse] = await Promise.all([
      conversationApi.getAvailableTools(),
      mcpApi.getMcpTools({ userId })
    ])

    // 解析 BUILTIN 工具
    let tools: ToolInfo[] = []
    if (Array.isArray(toolResponse)) {
      tools = toolResponse
    } else if (toolResponse?.code === 1 && toolResponse?.data) {
      tools = toolResponse.data
    }
    availableTools.value = tools

    // 解析 MCP 分组（用于展示）
    if (mcpResponse?.code === 1 && mcpResponse?.data) {
      mcpToolGroups.value = mcpResponse.data
      knownMcpServers.value = mcpResponse.data.map(g => ({ name: g.serverName }))
    } else {
      mcpToolGroups.value = []
      knownMcpServers.value = []
    }
  } catch {
    // 静默失败，工具列表为空
  }
}

/** 每次打开工具 popover 时刷新 MCP 连接状态 */
const refreshMcpStatus = async () => {
  const userId = localStorage.getItem('userId') || 'default_user'
  try {
    const mcpResponse = await mcpApi.getMcpTools({ userId })
    if (mcpResponse?.code === 1 && mcpResponse?.data) {
      mcpToolGroups.value = mcpResponse.data
      knownMcpServers.value = mcpResponse.data.map((g: McpServerGroup) => ({ name: g.serverName }))
    }
  } catch {
    // 静默失败
  }
}

/** MCP 工具分组数据（用于工具选择器展示） */
const mcpToolGroups = ref<McpServerGroup[]>([])
const managementDialogRef = ref()
const batchHideDialogRef = ref()
const insertPromptSelectorRef = ref()
const selectedInsertPromptIds = ref<number[]>([])
/** 扁平化所有可用工具（BUILTIN name + MCP fullName）用于过滤 */
const flatAvailableTools = computed(() => {
  const result: { name: string; description: string }[] = []
  for (const t of availableTools.value) {
    if (t.providerType === 'BUILTIN') {
      result.push({ name: t.name, description: t.description })
    }
  }
  // MCP 工具也加入备用（当没有从 /mcp/tools 获取到时）
  for (const t of availableTools.value) {
    if (t.providerType === 'MCP') {
      result.push({ name: t.name, description: t.description })
    }
  }
  return result
})

/** 过滤后的内置工具（未在会话默认中） */
const filteredBuiltinTools = computed(() => {
  return availableTools.value.filter(
    t => t.providerType === 'BUILTIN' && !conversationTools.value.includes(t.name)
  )
})

/**
 * 获取工具在 UI 中的展示名称。
 * BUILTIN 工具直接展示原名，MCP 工具展示为 "serverName / toolName"。
 */
const toolDisplayName = (name: string): string => {
  return getDisplayToolName(name, knownMcpServers.value)
}

onMounted(() => {
  loadConversations()
  loadAvailableTools()
})
</script>

<style scoped>
/* ===== Layout ===== */
.chat-container {
  height: 100vh;
  display: flex;
}

/* ===== Sidebar ===== */
.sidebar {
  background-color: var(--color-bg-sidebar);
  border-right: 1px solid var(--color-border-light);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: var(--space-5);
  border-bottom: 1px solid var(--color-border-light);
}

.sidebar-header h3 {
  margin: 0 0 var(--space-3) 0;
  font-family: var(--font-heading);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.sidebar-header-actions {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

/* Conversation list */
.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-3);
}

.loading-state,
.empty-conversations {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-10) var(--space-4);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
  gap: var(--space-2);
}

.empty-conversations p {
  margin: 0;
  font-size: var(--font-size-sm);
}

.conversation-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3);
  margin-bottom: var(--space-1);
  background-color: transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--transition-fast),
              transform var(--transition-fast),
              box-shadow var(--transition-fast);
}

.conversation-item:hover:not(.disabled) {
  background-color: var(--color-bg-surface);
  box-shadow: var(--shadow-sm);
  transform: translateX(2px);
}

.conversation-item.active {
  background-color: var(--color-primary-light);
  color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.conversation-item.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.conversation-info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex: 1;
  min-width: 0;
}

.conversation-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: inherit;
}

.conversation-actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex-shrink: 0;
}

.user-info {
  padding: var(--space-4);
  border-top: 1px solid var(--color-border-light);
}

.user-name {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  transition: color var(--transition-fast);
}

.user-name:hover {
  color: var(--color-text-primary);
}

/* ===== Main Chat ===== */
.chat-main {
  display: flex;
  flex-direction: column;
  padding: 0;
  background-color: var(--color-bg-surface);
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: var(--color-text-tertiary);
  gap: var(--space-3);
}

.empty-state h2 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.empty-state p {
  margin: 0;
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}

.chat-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* ===== Messages ===== */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-6);
}

/* === 无消息引导 === */
.messages-empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-tertiary);
  gap: var(--space-3);
  user-select: none;
}

.messages-empty-hint h3 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-secondary);
}

.messages-empty-hint p {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.message-item {
  display: flex;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
  animation: fadeInUp 350ms ease both;
}

/* Staggered message entrance */
.message-item:nth-child(1) { animation-delay: 0ms; }
.message-item:nth-child(2) { animation-delay: 60ms; }
.message-item:nth-child(3) { animation-delay: 120ms; }
.message-item:nth-child(4) { animation-delay: 180ms; }
.message-item:nth-child(5) { animation-delay: 240ms; }
.message-item:nth-child(6) { animation-delay: 300ms; }
.message-item:nth-child(7) { animation-delay: 360ms; }
.message-item:nth-child(8) { animation-delay: 420ms; }
.message-item:nth-child(9) { animation-delay: 480ms; }
.message-item:nth-child(10) { animation-delay: 540ms; }

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 38px;
  height: 38px;
  border-radius: var(--radius-full);
  background-color: var(--color-bg-page);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: transform var(--transition-fast);
}

.message-item.user .message-avatar {
  background-color: var(--color-primary);
  color: var(--color-text-inverse);
}

.message-item.assistant .message-avatar {
  background-color: var(--color-primary-light);
  color: var(--color-primary);
}

.message-avatar:hover {
  transform: scale(1.08);
}

.message-item.tool .message-avatar {
  background-color: #e8f5e9;
  color: #4caf50;
}

/* ===== Tool Calls Section (inside ASSISTANT messages) ===== */
.tool-calls-section {
  margin-bottom: var(--space-2);
}

/* ===== TOOL Message Compact ===== */
.tool-message-compact {
  max-width: 420px;
  border: 1px solid #c8e6c9;
  border-radius: var(--radius-md);
  background: #f1f8e9;
  overflow: hidden;
}

.tool-message-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-xs);
  color: #2e7d32;
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  user-select: none;
  transition: background-color var(--transition-fast);
}

.tool-message-header:hover {
  background-color: rgba(76, 175, 80, 0.06);
}

.tool-msg-chevron {
  font-size: 13px;
  transition: transform 0.25s ease;
  flex-shrink: 0;
}

.tool-msg-chevron.expanded {
  transform: rotate(90deg);
}

.tool-message-name {
  font-family: var(--font-mono);
  flex: 1;
}

.tool-message-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  background: rgba(76, 175, 80, 0.12);
  color: #388e3c;
}

.tool-message-body {
  overflow: hidden;
  transition: max-height 0.3s ease, padding 0.3s ease, border 0.3s ease;
  max-height: 20000px;
  padding: var(--space-2) var(--space-3);
  border-top: 1px solid rgba(76, 175, 80, 0.15);
}

.tool-message-body.collapsed {
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
  border-top-color: transparent;
}

.tool-message-body code {
  font-size: var(--font-size-xs);
  font-family: var(--font-mono);
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: var(--line-height-relaxed);
}

.tool-message-compact .message-time {
  padding: 0 var(--space-3) var(--space-2);
  margin-top: 0;
}

.message-content {
  max-width: 70%;
}

.message-text {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-lg);
  line-height: var(--line-height-relaxed);
  word-wrap: break-word;
  font-size: var(--font-size-base);
  white-space: pre-wrap;
}

.message-item.user .message-text {
  background-color: var(--color-primary);
  color: var(--color-text-inverse);
  border-bottom-right-radius: var(--radius-sm);
}

.message-item.assistant .message-text {
  background-color: var(--color-bg-page);
  color: var(--color-text-primary);
  border-bottom-left-radius: var(--radius-sm);
}

/* Segment rendering for messages with thinking blocks */
.message-text-segment {
  white-space: pre-wrap;
  word-wrap: break-word;
  word-break: break-word;
}

.message-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: var(--space-1);
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.message-time .el-button {
  font-size: 11px;
  padding: 2px 8px;
  height: 20px;
}

.message-item.user .message-time {
  justify-content: flex-end;
}

/* ===== Message Actions (Hover) ===== */
.message-bubble-wrapper {
  position: relative;
}

.message-actions {
  position: absolute;
  top: -12px;
  right: 0;
  display: flex;
  gap: 2px;
  opacity: 0;
  transform: translateY(4px);
  transition: opacity var(--transition-fast), transform var(--transition-fast);
  z-index: 10;
  background: var(--color-bg-surface);
  border-radius: var(--radius-md);
  padding: 2px;
  box-shadow: var(--shadow-sm);
}

.message-item.user .message-actions {
  right: auto;
  left: 0;
}

.message-bubble-wrapper:hover .message-actions,
.message-item:hover .message-actions {
  opacity: 1;
  transform: translateY(0);
}

.message-actions .el-button {
  font-size: 11px;
  padding: 2px 6px;
  height: 24px;
  min-width: 24px;
}

/* ===== Edit Mode ===== */
.message-edit-mode {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  background-color: var(--color-bg-page);
  border-radius: var(--radius-lg);
  border: 2px solid var(--color-primary-border);
  max-width: 520px;
  min-width: 340px;
}

.message-edit-mode.user {
  align-items: flex-end;
  border-bottom-right-radius: var(--radius-sm);
  background-color: var(--color-primary);
  border-color: var(--color-primary-border);
}

.message-edit-mode.assistant {
  align-items: flex-start;
  border-bottom-left-radius: var(--radius-sm);
  background-color: var(--color-bg-page);
}

/* === Edit section labels === */
.edit-section-label {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  margin-bottom: var(--space-1);
}

.message-edit-mode.user .edit-section-label {
  color: var(--color-text-inverse);
  opacity: 0.85;
}

.edit-thinking-section,
.edit-body-section {
  width: 100%;
}

/* === Edit inputs styled like message bubbles === */
.edit-thinking-input :deep(.el-textarea__inner),
.edit-body-input :deep(.el-textarea__inner) {
  resize: vertical;
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-relaxed);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  transition: border-color var(--transition-fast),
              box-shadow var(--transition-fast);
  font-family: inherit;
}

.edit-thinking-input :deep(.el-textarea__inner) {
  background-color: var(--color-bg-elevated, #f8f9fb);
  color: var(--color-text-secondary);
  font-style: italic;
}

.edit-thinking-input :deep(.el-textarea__inner):focus {
  border-color: var(--color-primary-border);
  box-shadow: 0 0 0 2px var(--color-primary-border);
  background-color: #fff;
}

.edit-body-input :deep(.el-textarea__inner) {
  background-color: var(--color-bg-surface);
  color: var(--color-text-primary);
}

.edit-body-input :deep(.el-textarea__inner):focus {
  border-color: var(--color-primary-border);
  box-shadow: 0 0 0 2px var(--color-primary-border);
  background-color: #fff;
}

.message-edit-mode.user .edit-thinking-input :deep(.el-textarea__inner) {
  background-color: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.85);
  border-color: rgba(255, 255, 255, 0.2);
}

.message-edit-mode.user .edit-body-input :deep(.el-textarea__inner) {
  background-color: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.95);
  border-color: rgba(255, 255, 255, 0.2);
}

.message-edit-mode.user .edit-thinking-input :deep(.el-textarea__inner):focus,
.message-edit-mode.user .edit-body-input :deep(.el-textarea__inner):focus {
  background-color: rgba(255, 255, 255, 0.22);
  border-color: rgba(255, 255, 255, 0.5);
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.25);
}

.message-edit-mode.user .edit-thinking-input :deep(.el-textarea__inner)::placeholder,
.message-edit-mode.user .edit-body-input :deep(.el-textarea__inner)::placeholder {
  color: rgba(255, 255, 255, 0.5);
}

.edit-warning {
  font-size: var(--font-size-xs);
  color: var(--color-warning);
  line-height: var(--line-height-relaxed);
  padding: var(--space-1) 0;
}

.edit-actions {
  display: flex;
  gap: var(--space-2);
}

/* Typing indicator */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: var(--space-3) var(--space-4);
  background-color: var(--color-bg-page);
  border-radius: var(--radius-lg);
  border-bottom-left-radius: var(--radius-sm);
  width: fit-content;
}

.typing-indicator span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background-color: var(--color-text-tertiary);
  animation: typingBounce 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) { animation-delay: 0s; }
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typingBounce {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-8px);
    opacity: 1;
  }
}

/* ===== Input Area ===== */
.input-container {
  padding: var(--space-4) var(--space-6);
  border-top: 1px solid var(--color-border-light);
  display: flex;
  gap: var(--space-3);
  align-items: flex-end;
  background-color: var(--color-bg-surface);
}

.input-container :deep(.el-textarea) {
  flex: 1;
}

.input-container :deep(.el-textarea__inner) {
  resize: none;
  border-radius: var(--radius-lg);
  background-color: var(--color-bg-page);
  border-color: var(--color-border);
  font-size: var(--font-size-base);
  line-height: var(--line-height-normal);
  padding: var(--space-3) var(--space-4);
}

.input-container :deep(.el-textarea__inner):focus {
  border-color: var(--color-primary-border);
  background-color: var(--color-bg-surface);
  box-shadow: 0 0 0 2px var(--color-primary-border);
}

/* ===== Chat Toolbar ===== */
.chat-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2) var(--space-6);
  border-bottom: 1px solid var(--color-border-light);
  background-color: var(--color-bg-page);
  min-height: 40px;
  flex-shrink: 0;
}

.tool-indicators {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.tool-badge {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.tool-tag {
  margin: 0 2px;
}

/* ===== Function Selector Popover ===== */
.func-selector-content {
  font-size: var(--font-size-sm);
}

.func-item-btn {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  justify-content: flex-start;
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  padding: var(--space-2) var(--space-3);
}

.func-item-btn:hover:not(.is-disabled) {
  color: var(--color-primary);
  background-color: var(--color-primary-light);
}

.func-item-active {
  color: var(--color-primary);
  background-color: var(--color-primary-light);
  font-weight: var(--font-weight-medium);
}

.deep-thinking-check {
  margin-left: auto;
  font-size: 14px;
  color: var(--color-primary);
}

/* ===== 功能栏 badge ===== */
.func-badge {
  margin-left: var(--space-1);
}

.func-badge-right {
  margin-left: auto;
}

/* ===== Deep Thinking Indicator ===== */
.deep-thinking-tag {
  --el-tag-bg-color: var(--color-primary-light);
  --el-tag-border-color: var(--color-primary-border);
  --el-tag-text-color: var(--color-primary);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* ===== Tool Selector Button ===== */
.tool-selector-btn {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  flex-shrink: 0;
  transition: color var(--transition-fast);
}

.tool-selector-btn:hover {
  color: var(--color-primary);
}

/* ===== Tool Selector Popover ===== */
.tool-selector-content {
  font-size: var(--font-size-sm);
}

.tool-section {
  margin-bottom: var(--space-3);
}

.tool-section-title {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-bottom: var(--space-2);
  font-weight: var(--font-weight-medium);
}

.no-default-tools {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  font-style: italic;
}

.tool-checkbox-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.tool-checkbox-list :deep(.el-checkbox) {
  margin-right: 0;
  max-width: 100%;
  align-items: center;
}

.tool-checkbox-list :deep(.el-checkbox__label) {
  max-width: calc(100% - 20px);
  overflow: hidden;
}

.tool-checkbox-name {
  font-weight: var(--font-weight-semibold);
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
}

.tool-checkbox-desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
  max-width: 100%;
}

.tool-mode-section :deep(.el-radio) {
  margin-right: 0;
}

/* ===== 注销账号菜单项 ===== */
.deactivate-item {
  color: var(--color-danger, #f56c6c) !important;
}

/* ===== MCP Tool Subsections (in popover) ===== */
.tool-subsection {
  margin-bottom: var(--space-2);
}

.tool-subsection-title {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-medium);
  margin-bottom: var(--space-2);
}

.mcp-subsection-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  justify-content: space-between;
}

/* ===== 隐藏消息样式 ===== */
.message-hidden .message-bubble-wrapper {
  background: rgba(128, 128, 128, 0.08);
  border-radius: var(--radius-lg);
}

.hidden-indicator {
  position: absolute;
  bottom: 4px;
  right: 4px;
  color: var(--color-text-tertiary);
  opacity: 0.5;
  pointer-events: none;
}


</style>