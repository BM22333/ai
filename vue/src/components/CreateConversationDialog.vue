<template>
  <el-dialog
    v-model="dialogVisible"
    title="创建新会话"
    width="540px"
    :close-on-click-modal="false"
    @open="onDialogOpen"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <!-- 加载已保存配置下拉框 -->
      <el-form-item label="加载配置">
        <el-select
          v-model="selectedConfigId"
          placeholder="选择已保存的配置（可选）"
          clearable
          @change="onConfigSelect"
          :loading="loadingConfig"
          style="width: 100%"
        >
          <el-option
            v-for="config in savedConfigs"
            :key="config.id"
            :label="config.configName"
            :value="config.id"
          >
            <div class="config-option">
              <span class="config-option-name">{{ config.configName }}</span>
              <el-tag size="small" type="primary" effect="light">{{ config.modelName }}</el-tag>
            </div>
          </el-option>
        </el-select>
        <div v-if="savedConfigs.length === 0 && !loadingConfig" class="no-config-hint">
          暂无已保存的配置，请先前往
          <el-link type="primary" @click="goToApiConfig">API 配置管理</el-link>
          添加
        </div>
      </el-form-item>

      <!-- 已加载配置提示条 -->
      <el-form-item v-if="savedConfigLoaded" label=" ">
        <div class="saved-config-bar">
          <el-icon :size="16" color="#67c23a"><SuccessFilled /></el-icon>
          <span>已加载：{{ loadedConfigName }}</span>
          <el-button text size="small" type="warning" @click="clearSavedConfig">清除</el-button>
        </div>
      </el-form-item>

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
          placeholder="请输入API Key"
          show-password
        />
      </el-form-item>

      <el-form-item label="模型" prop="model">
        <el-input
          v-model="form.model"
          placeholder="请输入模型名称，例如：deepseek-ai/DeepSeek-V3"
        />
      </el-form-item>

      <el-form-item label="Base URL" prop="baseUrl">
        <el-input
          v-model="form.baseUrl"
          placeholder="API基础URL（可选）"
        />
      </el-form-item>

      <el-form-item label="系统提示词" prop="systemPrompt">
        <el-input
          v-model="form.systemPrompt"
          type="textarea"
          :rows="4"
          placeholder="系统提示词（可选）"
        />
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
          💡 勾选的工具在每次对话中默认启用。对话时还可以临时追加其他工具。
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleCreate" :loading="loading">
        创建
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { SuccessFilled, Setting, Box, Search, Refresh, QuestionFilled } from '@element-plus/icons-vue'
import { conversationApi } from '../api/conversation'
import { mcpApi } from '../api/mcp'
import { userApi } from '../api/user'
import type { UserApi, ToolInfo, McpServerGroup, McpToolInfo } from '../types'

const router = useRouter()
const emit = defineEmits(['created'])

const dialogVisible = ref(false)
const loading = ref(false)
const loadingConfig = ref(false)
const loadingTools = ref(false)
const formRef = ref<FormInstance>()
const savedConfigLoaded = ref(false)
const loadedConfigName = ref('')
const savedConfigs = ref<UserApi[]>([])
const selectedConfigId = ref<number | ''>('')
const builtinTools = ref<ToolInfo[]>([])
const mcpGroups = ref<McpServerGroup[]>([])
const selectedTools = ref<string[]>([])
const reconnectingServer = ref<string | null>(null)
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

const DEFAULT_MODEL = 'deepseek-ai/DeepSeek-V4-Flash'
const DEFAULT_BASE_URL = 'https://api.siliconflow.cn'
const DEFAULT_SYSTEM_PROMPT = '你是一只叫做小雨的猫娘，喜欢唱跳rap篮球。'

const form = reactive({
  conversationName: '',
  apiKey: '',
  model: DEFAULT_MODEL,
  baseUrl: DEFAULT_BASE_URL,
  systemPrompt: DEFAULT_SYSTEM_PROMPT,
  maxMessages: 100,
  maxToolCallDepth: 10,
  afterHistoryPrompt: '',
  temperature: 1.0,
  reasoningEffort: 'high'
})

const rules: FormRules = {
  conversationName: [
    { required: true, message: '请输入会话名称', trigger: 'blur' }
  ],
  apiKey: [
    { required: true, message: '请输入API Key', trigger: 'blur' }
  ],
  model: [
    { required: true, message: '请选择模型', trigger: 'change' }
  ]
}

const resetForm = () => {
  form.conversationName = ''
  form.apiKey = ''
  form.model = DEFAULT_MODEL
  form.baseUrl = DEFAULT_BASE_URL
  form.systemPrompt = DEFAULT_SYSTEM_PROMPT
  form.maxMessages = 100
  form.maxToolCallDepth = 10
  form.afterHistoryPrompt = ''
  form.temperature = 1.0
  form.reasoningEffort = 'high'
  savedConfigLoaded.value = false
  loadedConfigName.value = ''
  selectedConfigId.value = ''
  selectedTools.value = []
  toolSearchText.value = ''
  formRef.value?.clearValidate()
}

const goToApiConfig = () => {
  dialogVisible.value = false
  router.push('/api-config')
}

const onDialogOpen = () => {
  loadSavedConfigs()
  loadAvailableTools()
}

const loadAvailableTools = async () => {
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

const loadSavedConfigs = async () => {
  loadingConfig.value = true
  try {
    const userId = localStorage.getItem('userId') || 'default_user'
    const response = await userApi.getApiConfigs({ userId })

    if (response.code === 1 && response.data) {
      savedConfigs.value = response.data
    } else {
      savedConfigs.value = []
    }
  } catch {
    savedConfigs.value = []
  } finally {
    loadingConfig.value = false
  }
}

const onConfigSelect = (configId: number | '') => {
  if (!configId) {
    clearSavedConfig()
    return
  }
  const config = savedConfigs.value.find(c => c.id === configId)
  if (config) {
    form.apiKey = config.apiKey || ''
    form.model = config.modelName || DEFAULT_MODEL
    form.baseUrl = config.baseUrl || DEFAULT_BASE_URL
    savedConfigLoaded.value = true
    loadedConfigName.value = config.configName
  }
}

const clearSavedConfig = () => {
  form.apiKey = ''
  form.model = DEFAULT_MODEL
  form.baseUrl = DEFAULT_BASE_URL
  form.maxMessages = 100
  form.maxToolCallDepth = 10
  form.afterHistoryPrompt = ''
  form.temperature = 1.0
  form.reasoningEffort = 'high'
  savedConfigLoaded.value = false
  loadedConfigName.value = ''
  selectedConfigId.value = ''
}

const open = () => {
  resetForm()
  dialogVisible.value = true
}

const handleCreate = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const userId = localStorage.getItem('userId') || 'default_user'
        const response = await conversationApi.createConversation({
          ...form,
          userId
        })

        if (response.code === 1 && response.data) {
          const conversationId = response.data

          // 如果选择了工具，创建后立即设置工具偏好
          if (selectedTools.value.length > 0 && conversationId) {
            try {
              await conversationApi.updateConversation({
                conversationId,
                toolPreferences: JSON.stringify(selectedTools.value)
              })
            } catch (e) {
              console.warn('设置工具偏好失败:', e)
            }
          }

          ElMessage.success('会话创建成功！')
          dialogVisible.value = false
          emit('created', conversationId)

          formRef.value?.resetFields()
        } else {
          ElMessage.error(response.msg || '创建失败')
        }
      } catch (error: any) {
        ElMessage.error(error.msg || '创建失败')
      } finally {
        loading.value = false
      }
    }
  })
}

defineExpose({ open })
</script>

<style scoped>
.config-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.config-option-name {
  font-weight: var(--font-weight-medium);
  margin-right: var(--space-2);
}

.no-config-hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: var(--space-1);
}

.saved-config-bar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  background: var(--color-success-light);
  border: 1px solid var(--color-success-border);
  border-radius: var(--radius-md);
  font-size: var(--font-size-xs);
  color: var(--color-success);
  width: 100%;
  margin-top: -8px;
}

.saved-config-bar .el-button {
  margin-left: auto;
}

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

/* ---- MCP 状态指示 ---- */
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

/* ---- 工具列表容器 ---- */
.tools-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: 100%;
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

/* ---- 工具名称 ---- */
.tool-name {
  font-weight: var(--font-weight-semibold);
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  line-height: var(--line-height-tight);
}

/* ---- 工具描述（2 行截断 + 悬浮显示完整） ---- */
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
