<template>
  <el-dialog
    v-model="dialogVisible"
    title="会话设置"
    width="560px"
    :close-on-click-modal="false"
    @open="onDialogOpen"
  >
    <el-form :model="form" ref="formRef" label-width="110px">
      <el-form-item label="会话名称" prop="conversationName">
        <el-input
          v-model="form.conversationName"
          placeholder="请输入会话名称"
          maxlength="50"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="API Key" prop="apiKey">
        <el-input
          v-model="form.apiKey"
          type="password"
          placeholder="留空则不修改"
          show-password
        />
      </el-form-item>

      <el-form-item label="模型" prop="model">
        <el-input
          v-model="form.model"
          placeholder="留空则不修改"
        />
      </el-form-item>

      <el-form-item label="Base URL" prop="baseUrl">
        <el-input
          v-model="form.baseUrl"
          placeholder="留空则不修改"
        />
      </el-form-item>

      <el-form-item label="系统提示词" prop="systemPrompt">
        <div class="system-prompt-row">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="4"
            placeholder="系统提示词（可选）"
            class="system-prompt-input"
          />
          <el-button
            type="primary"
            plain
            size="small"
            class="expand-edit-btn"
            @click="openSystemPromptEditor"
          >
            <el-icon><FullScreen /></el-icon>
            全屏编辑
          </el-button>
        </div>
      </el-form-item>

      <el-form-item label=" ">
        <el-collapse style="width: 100%">
          <el-collapse-item title="高级设置" name="advanced">
            <el-form-item label-width="150px">
              <template #label>
                <span>最大消息数</span>
                <el-tooltip
                  placement="right"
                  effect="light"
                  :popper-style="{ maxWidth: '360px' }"
                >
                  <template #content>
                    <div class="info-tooltip-content">
                      控制每次对话时发送给 AI 的历史消息数量上限。<br/>
                      值越大，AI 能"看到"的上下文越多，但消耗的 token 也越多。<br/>
                      默认值 100 表示 AI 最多看到最近的 100 条消息。<br/>
                      设为 0 或负数则回退到服务端默认值。<br/>
                      <strong>注意：</strong>被隐藏（isSeeable=false）的消息不计入此上限。
                    </div>
                  </template>
                  <el-icon class="info-icon"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input-number
                v-model="form.maxMessages"
                :min="1"
                :max="1000"
                :step="1"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label-width="150px">
              <template #label>
                <span>最大工具调用深度</span>
                <el-tooltip
                  placement="right"
                  effect="light"
                  :popper-style="{ maxWidth: '360px' }"
                >
                  <template #content>
                    <div class="info-tooltip-content">
                      限制单次对话中工具调用的轮次上限。<br/>
                      每轮包括一次 ASSISTANT(tool_calls) 及其跟随的 TOOL 结果消息。<br/>
                      超过此深度后 SSE 流将返回 error 事件并终止。<br/>
                      默认值为 10，设为 0 或负数则回退默认值。
                    </div>
                  </template>
                  <el-icon class="info-icon"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input-number
                v-model="form.maxToolCallDepth"
                :min="1"
                :max="50"
                :step="1"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label-width="150px">
              <template #label>
                <span>历史后置提示词</span>
                <el-tooltip
                  placement="right"
                  effect="light"
                  :popper-style="{ maxWidth: '360px' }"
                >
                  <template #content>
                    <div class="info-tooltip-content">
                      紧跟在历史消息之后、用户当前消息之前注入给 AI 的指令。<br/>
                      以 <code>system</code> 角色发送，AI 将其视为系统指令。<br/>
                      常用于设定回复风格、格式要求等场景级规则。<br/>
                      留空则不注入。
                    </div>
                  </template>
                  <el-icon class="info-icon"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input
                v-model="form.afterHistoryPrompt"
                type="textarea"
                :rows="3"
                placeholder="紧跟历史消息之后发给 AI 的附加指令（可选）"
              />
            </el-form-item>
            <el-form-item label-width="150px">
              <template #label>
                <span>温度</span>
                <el-tooltip
                  placement="right"
                  effect="light"
                  :popper-style="{ maxWidth: '360px' }"
                >
                  <template #content>
                    <div class="info-tooltip-content">
                      控制 AI 回复的随机性和创造性。<br/>
                      取值范围 0~2，默认 0.7。<br/>
                      较低的值（如 0.1）使回复更确定、一致；<br/>
                      较高的值（如 1.5）使回复更多样、有创意。<br/>
                      <strong>注意：</strong>温度和推理强度不建议同时修改。
                    </div>
                  </template>
                  <el-icon class="info-icon"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input-number
                v-model="form.temperature"
                :min="0"
                :max="2"
                :step="0.1"
                :precision="1"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label-width="150px">
              <template #label>
                <span>推理强度</span>
                <el-tooltip
                  placement="right"
                  effect="light"
                  :popper-style="{ maxWidth: '360px' }"
                >
                  <template #content>
                    <div class="info-tooltip-content">
                      控制模型在回复前的内部推理深度。<br/>
                      从低到高：none → minimal → low → medium → high → xhigh → max。<br/>
                      更高的推理强度通常产生更准确的回答，但耗时更长。<br/>
                      默认值为 medium。<br/>
                      <strong>注意：</strong>仅支持推理强度调节的模型生效。
                    </div>
                  </template>
                  <el-icon class="info-icon"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input
                v-model="form.reasoningEffort"
                placeholder="例如：low / medium / high / xhigh / max"
              />
            </el-form-item>
          </el-collapse-item>
        </el-collapse>
      </el-form-item>

      <el-form-item label="默认启用的工具">
        <div v-if="loadingTools" class="tools-loading">
          <el-icon class="is-loading"><Setting /></el-icon>
          <span>加载工具列表...</span>
        </div>
        <div v-else-if="builtinTools.length === 0 && mcpGroups.length === 0" class="no-tools-hint">
          暂无可用的工具
        </div>
        <div v-else class="tools-wrapper">
          <!-- 搜索栏 -->
          <el-input
            v-model="toolSearchText"
            placeholder="搜索工具..."
            clearable
            size="small"
            :prefix-icon="Search"
            class="tool-search-input"
          />
          <!-- 可滚动区域 -->
          <div class="tools-scroll">
            <div class="tools-sections">
              <!-- 内置工具 -->
              <div v-if="filteredBuiltinTools.length > 0" class="tool-group-section">
                <div class="tool-group-title">
                  <el-icon :size="14"><Box /></el-icon>
                  <span>内置工具</span>
                </div>
                <el-checkbox-group v-model="selectedTools" class="tools-list">
                  <el-tooltip
                    v-for="tool in filteredBuiltinTools"
                    :key="tool.name"
                    :content="tool.description"
                    placement="top-start"
                    :show-after="600"
                    effect="light"
                    :popper-style="{ maxWidth: '400px' }"
                  >
                    <label
                      class="tool-item"
                      :class="{ 'is-checked': selectedTools.includes(tool.name) }"
                    >
                      <el-checkbox :label="tool.name" :value="tool.name">
                        <span class="tool-name">{{ tool.name }}</span>
                      </el-checkbox>
                      <span class="tool-desc">{{ tool.description }}</span>
                    </label>
                  </el-tooltip>
                </el-checkbox-group>
              </div>

              <!-- MCP 工具（按 Server 分组） -->
              <template v-for="group in mcpGroups" :key="group.serverName">
                <div
                  v-if="filterMcpGroupTools(group).length > 0"
                  class="tool-group-section"
                >
                  <div class="tool-group-title mcp-group-title">
                    <span class="connection-dot" :class="{ connected: group.connected }"></span>
                    <span>MCP: {{ group.serverName }}</span>
                    <el-tag
                      :type="group.connected ? 'success' : 'danger'"
                      size="small"
                      effect="light"
                      class="mcp-status-tag"
                    >
                      {{ group.connected ? '已连接' : '已断开' }}
                    </el-tag>
                    <el-button
                      v-if="!group.connected"
                      text
                      size="small"
                      type="warning"
                      :loading="reconnectingServer === group.serverName"
                      @click.stop="quickReconnect(group.serverName)"
                    >
                      <el-icon><Refresh /></el-icon>
                      重连
                    </el-button>
                  </div>
                  <el-checkbox-group v-model="selectedTools" class="tools-list">
                    <el-tooltip
                      v-for="tool in filterMcpGroupTools(group)"
                      :key="tool.fullName"
                      :content="tool.description"
                      placement="top-start"
                      :show-after="600"
                      effect="light"
                      :popper-style="{ maxWidth: '400px' }"
                    >
                      <label
                        class="tool-item"
                        :class="{ 'is-checked': selectedTools.includes(tool.fullName) }"
                      >
                        <el-checkbox :label="tool.fullName" :value="tool.fullName">
                          <span class="tool-name">{{ tool.displayName }}</span>
                          <span class="tool-server-badge">{{ group.serverName }}</span>
                        </el-checkbox>
                        <span class="tool-desc">{{ tool.description }}</span>
                      </label>
                    </el-tooltip>
                  </el-checkbox-group>
                </div>
              </template>
            </div>
          </div>
        </div>
        <div class="tools-tip">
          💡 勾选的工具在每次对话中默认启用。对话时还可以通过 🔧 按钮临时追加。
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSave" :loading="saving">
        保存设置
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Setting, FullScreen, Box, Search, Refresh, QuestionFilled } from '@element-plus/icons-vue'
import { conversationApi } from '../api/conversation'
import { mcpApi } from '../api/mcp'
import type { ToolInfo, McpServerGroup, McpToolInfo } from '../types'

const router = useRouter()

const emit = defineEmits(['updated'])

const dialogVisible = ref(false)
const saving = ref(false)
const loadingTools = ref(false)
const formRef = ref()
const reconnectingServer = ref<string | null>(null)

const builtinTools = ref<ToolInfo[]>([])
const mcpGroups = ref<McpServerGroup[]>([])
const selectedTools = ref<string[]>([])
const toolSearchText = ref('')

/** 搜索过滤后的内置工具 */
const filteredBuiltinTools = computed(() => {
  const q = toolSearchText.value.toLowerCase().trim()
  if (!q) return builtinTools.value
  return builtinTools.value.filter(
    t => t.name.toLowerCase().includes(q) || t.description.toLowerCase().includes(q)
  )
})

/** 过滤指定 MCP 分组下的工具 */
const filterMcpGroupTools = (group: McpServerGroup): McpToolInfo[] => {
  const q = toolSearchText.value.toLowerCase().trim()
  if (!q) return group.tools
  return group.tools.filter(
    t => t.displayName.toLowerCase().includes(q) || t.description.toLowerCase().includes(q) ||
      t.fullName.toLowerCase().includes(q)
  )
}

/** 快速重连断开的 MCP Server */
const quickReconnect = async (serverName: string) => {
  reconnectingServer.value = serverName
  try {
    const userId = localStorage.getItem('userId') || 'default_user'
    const res = await mcpApi.reconnectServer(serverName, { userId })
    if (res.code === 1) {
      ElMessage.success(`${serverName} 已重新连接`)
      // 刷新 MCP 分组数据
      const mcpResponse = await mcpApi.getMcpTools({ userId })
      if (mcpResponse?.code === 1 && mcpResponse?.data) {
        mcpGroups.value = mcpResponse.data
      }
    } else {
      ElMessage.error(res.msg || '重连失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.msg || '重连失败')
  } finally {
    reconnectingServer.value = null
  }
}

const form = reactive({
  conversationId: '',
  conversationName: '',
  apiKey: '',
  model: '',
  baseUrl: '',
  systemPrompt: '',
  maxMessages: 100,
  maxToolCallDepth: 10,
  afterHistoryPrompt: '',
  temperature: 1.0,
  reasoningEffort: 'high'
})

const onDialogOpen = async () => {
  loadingTools.value = true
  const userId = localStorage.getItem('userId') || 'default_user'
  try {
    // 并行获取 BUILTIN 和 MCP 工具
    const [builtinResponse, mcpResponse] = await Promise.all([
      conversationApi.getAvailableTools(),
      mcpApi.getMcpTools({ userId })
    ])

    // 解析 BUILTIN 工具
    let tools: ToolInfo[] = []
    if (Array.isArray(builtinResponse)) {
      tools = builtinResponse
    } else if (builtinResponse?.code === 1 && builtinResponse?.data) {
      tools = builtinResponse.data
    }
    builtinTools.value = tools.filter(t => t.providerType === 'BUILTIN')

    // 解析 MCP 分组工具
    if (mcpResponse?.code === 1 && mcpResponse?.data) {
      mcpGroups.value = mcpResponse.data
    } else {
      mcpGroups.value = []
    }
  } catch {
    builtinTools.value = []
    mcpGroups.value = []
  } finally {
    loadingTools.value = false
  }
}

const open = (conversation: {
  conversationId: string
  conversationName?: string
  apiKey?: string
  model?: string
  baseUrl?: string
  systemPrompt?: string
  toolPreferences?: string
  maxMessages?: number
  maxToolCallDepth?: number
  afterHistoryPrompt?: string
  temperature?: number
  reasoningEffort?: string
}) => {
  form.conversationId = conversation.conversationId
  form.conversationName = conversation.conversationName || ''
  form.apiKey = conversation.apiKey || ''
  form.model = conversation.model || ''
  form.baseUrl = conversation.baseUrl || ''
  form.systemPrompt = conversation.systemPrompt || ''
  form.maxMessages = conversation.maxMessages ?? 100
  form.maxToolCallDepth = conversation.maxToolCallDepth ?? 10
  form.afterHistoryPrompt = conversation.afterHistoryPrompt || ''
  form.temperature = conversation.temperature ?? 1.0
  form.reasoningEffort = conversation.reasoningEffort || 'high'

  if (conversation.toolPreferences) {
    try {
      selectedTools.value = JSON.parse(conversation.toolPreferences)
    } catch {
      selectedTools.value = []
    }
  } else {
    selectedTools.value = []
  }

  dialogVisible.value = true
}

const handleSave = async () => {
  saving.value = true
  try {
    const updateData: any = {
      conversationId: form.conversationId,
      conversationName: form.conversationName,
      systemPrompt: form.systemPrompt,
      toolPreferences: JSON.stringify(selectedTools.value),
      maxMessages: form.maxMessages,
      maxToolCallDepth: form.maxToolCallDepth,
      afterHistoryPrompt: form.afterHistoryPrompt,
      temperature: form.temperature,
      reasoningEffort: form.reasoningEffort
    }

    // sensitive / optional fields: 留空则不修改
    if (form.apiKey.trim()) updateData.apiKey = form.apiKey.trim()
    if (form.model.trim()) updateData.model = form.model.trim()
    if (form.baseUrl.trim()) updateData.baseUrl = form.baseUrl.trim()

    const response = await conversationApi.updateConversation(updateData)

    if (response.code === 1) {
      ElMessage.success('会话设置已保存')
      dialogVisible.value = false
      emit('updated', {
        ...form,
        toolPreferences: JSON.stringify(selectedTools.value)
      })
    } else {
      ElMessage.error(response.msg || '保存失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.msg || '保存失败')
  } finally {
    saving.value = false
  }
}

const openSystemPromptEditor = () => {
  // 关闭对话框，导航到全屏编辑页面
  dialogVisible.value = false
  router.push(`/system-prompt-editor/${form.conversationId}`)
}

defineExpose({ open })
</script>

<style scoped>
/* ---- 系统提示词行 ---- */
.system-prompt-row {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: 100%;
}

.system-prompt-input {
  width: 100%;
}

.expand-edit-btn {
  align-self: flex-end;
  font-size: var(--font-size-xs);
}

/* ---- 工具加载 & 空态 ---- */
.tools-loading {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.no-tools-hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

/* ---- 工具外层容器 ---- */
.tools-wrapper {
  width: 100%;
}

/* ---- 搜索栏 ---- */
.tool-search-input {
  margin-bottom: var(--space-3);
}

/* ---- 可滚动区域 ---- */
.tools-scroll {
  max-height: 320px;
  overflow-y: auto;
  padding-right: var(--space-1);
}

/* ---- 工具分组容器 ---- */
.tools-sections {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  width: 100%;
}

/* ---- 工具分组标题 ---- */
.tool-group-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.tool-group-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  padding: var(--space-1) 0;
}

.tool-group-title.mcp-group-title {
  color: var(--color-text-secondary);
}

/* ---- 工具列表容器 ---- */
.tools-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: 100%;
}

/* ---- MCP 状态 ---- */
.connection-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f44336;
  flex-shrink: 0;
}

.connection-dot.connected {
  background: #4caf50;
  box-shadow: 0 0 4px rgba(76, 175, 80, 0.5);
}

.mcp-status-tag {
  margin-left: auto;
}

/* ---- 工具项卡片 ---- */
.tool-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--transition-fast),
              border-color var(--transition-fast),
              box-shadow var(--transition-fast);
  background: var(--color-bg-surface);
}

.tool-item:hover {
  background-color: var(--color-bg-page);
  border-color: var(--color-border);
}

.tool-item.is-checked {
  background-color: var(--color-primary-light);
  border-color: var(--color-primary-border);
  box-shadow: 0 0 0 1px var(--color-primary-border);
}

/* ---- 复选框内部样式 ---- */
.tool-item :deep(.el-checkbox) {
  margin-right: 0;
  width: 100%;
  align-items: flex-start;
}

.tool-item :deep(.el-checkbox__label) {
  width: 100%;
  padding-left: var(--space-2);
}

/* ---- 工具名称（monospace，醒目） ---- */
.tool-name {
  font-weight: var(--font-weight-semibold);
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  line-height: var(--line-height-tight);
}

/* ---- MCP Server 标记 ---- */
.tool-server-badge {
  display: inline-block;
  margin-left: var(--space-2);
  font-size: 11px;
  padding: 0 6px;
  border-radius: var(--radius-sm);
  background: var(--color-bg-page);
  color: var(--color-text-tertiary);
  font-weight: var(--font-weight-normal);
  font-family: var(--font-mono);
  vertical-align: middle;
}

/* ---- 工具描述（行截断 + 悬浮完整提示） ---- */
.tool-desc {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  line-height: var(--line-height-relaxed);
  max-width: 100%;
  word-break: break-word;
  padding-left: calc(var(--space-5) + var(--space-2));
}

/* ---- 底部提示 ---- */
.tools-tip {
  margin-top: var(--space-3);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  line-height: var(--line-height-relaxed);
}

/* ---- 高级设置信息图标 ---- */
.info-icon {
  margin-left: var(--space-1);
  font-size: 14px;
  color: var(--color-text-tertiary);
  cursor: help;
  vertical-align: middle;
  transition: color var(--transition-fast);
}

.info-icon:hover {
  color: var(--color-primary);
}

.info-tooltip-content {
  font-size: var(--font-size-xs);
  line-height: 1.7;
  color: var(--color-text-secondary);
}
</style>
