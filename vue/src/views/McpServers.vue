<template>
  <div class="mcp-servers-page">
    <div class="page-header">
      <div class="header-left">
        <el-button text size="small" @click="goBack" class="back-btn">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <h2>MCP Server 管理</h2>
        <p class="header-desc">管理远程 MCP 工具服务器，支持自定义鉴权请求头</p>
      </div>
      <el-button type="primary" @click="openAddDialog">
        <el-icon><Plus /></el-icon>
        添加 MCP Server
      </el-button>
    </div>

    <!-- 服务器列表 -->
    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading" :size="32"><Setting /></el-icon>
      <span>加载中...</span>
    </div>

    <div v-else-if="servers.length === 0" class="empty-state">
      <el-icon :size="48" color="#909399"><Connection /></el-icon>
      <p>暂无 MCP Server</p>
      <el-button type="primary" plain @click="openAddDialog">添加第一个 Server</el-button>
    </div>

    <div v-else class="server-list">
      <div
        v-for="server in servers"
        :key="server.name"
        class="server-card"
        :class="{ 'is-disconnected': !server.connected }"
      >
        <div class="server-card-header">
          <div class="server-name-row">
            <span class="server-name">{{ server.name }}</span>
            <el-tag
              :type="server.connected ? 'success' : 'danger'"
              size="small"
              effect="light"
            >
              <span class="connection-dot" :class="{ connected: server.connected }"></span>
              {{ server.connected ? '已连接' : '已断开' }}
            </el-tag>
          </div>
          <div class="server-url">{{ server.baseUrl }}</div>
        </div>

        <div class="server-card-body">
          <!-- 鉴权头展示（脱敏） -->
          <div v-if="server.headers && Object.keys(server.headers).length > 0" class="server-auth">
            <span class="auth-label">鉴权：</span>
            <el-tag
              v-for="(value, key) in server.headers"
              :key="key"
              size="small"
              type="warning"
              effect="light"
              class="auth-tag"
            >
              {{ key }}: {{ maskValue(String(value)) }}
            </el-tag>
          </div>

          <!-- 断开提示 -->
          <div v-if="!server.connected" class="disconnected-hint">
            <el-icon><WarningFilled /></el-icon>
            连接已断开，请点击重连按钮恢复连接
          </div>

          <!-- 工具列表 -->
          <div v-if="server.tools && server.tools.length > 0" class="server-tools">
            <span class="tools-label">可用工具：</span>
            <el-tag
              v-for="tool in server.tools"
              :key="tool.fullName"
              size="small"
              type="info"
              effect="light"
              class="tool-tag"
            >
              {{ tool.displayName }}
            </el-tag>
          </div>
          <div v-else-if="server.connected" class="no-tools-hint">该 Server 下暂无可用工具</div>
        </div>

        <div class="server-card-actions">
          <el-button
            text
            size="small"
            type="primary"
            :loading="reconnecting === server.name"
            @click="handleReconnect(server.name)"
          >
            <el-icon><Refresh /></el-icon>
            重连
          </el-button>
          <el-popconfirm
            title="确定要删除此 Server 吗？删除后工具将自动注销。"
            confirm-button-text="删除"
            cancel-button-text="取消"
            @confirm="handleDelete(server.name)"
          >
            <template #reference>
              <el-button text size="small" type="danger">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>
    </div>

    <!-- 添加/编辑 Server 对话框 -->
    <el-dialog
      v-model="showAddDialog"
      :title="dialogTitle"
      width="580px"
      :close-on-click-modal="false"
      @closed="resetAddForm"
    >
      <el-form :model="addForm" ref="addFormRef" label-width="110px" @submit.prevent>
        <el-form-item
          label="名称"
          prop="name"
          :rules="[{ required: true, message: '请输入名称', trigger: 'blur' }]"
        >
          <el-input
            v-model="addForm.name"
            placeholder="如 weather、tavily-search"
            maxlength="50"
          />
        </el-form-item>

        <el-form-item
          label="端点 URL"
          prop="baseUrl"
          :rules="[{ required: true, message: '请输入 URL', trigger: 'blur' }]"
        >
          <el-input
            v-model="addForm.baseUrl"
            placeholder="https://weather-mcp.example.com/mcp"
          />
        </el-form-item>

        <!-- 自定义请求头 -->
        <el-form-item label="请求头（可选）">
          <div class="headers-section">
            <!-- 预设快捷按钮 -->
            <div class="header-presets">
              <span class="presets-label">常用预设：</span>
              <el-button size="small" text type="primary" @click="applyPreset('bearer')">
                Bearer Token
              </el-button>
              <el-button size="small" text type="primary" @click="applyPreset('apikey')">
                API Key
              </el-button>
              <el-button size="small" text type="primary" @click="applyPreset('basic')">
                Basic Auth
              </el-button>
            </div>

            <!-- Header 键值对列表 -->
            <div
              v-for="(header, index) in addForm.headerEntries"
              :key="index"
              class="header-entry"
            >
              <el-input
                v-model="header.key"
                placeholder="Header Key"
                class="header-key-input"
              />
              <el-input
                v-model="header.value"
                :placeholder="getValuePlaceholder(header.key)"
                class="header-value-input"
                :type="isSensitiveKey(header.key) ? 'password' : 'text'"
                show-password
              />
              <el-button
                text
                type="danger"
                size="small"
                @click="removeHeader(index)"
                :disabled="addForm.headerEntries.length <= 1"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>

            <el-button
              text
              size="small"
              type="primary"
              @click="addHeader"
              class="add-header-btn"
            >
              <el-icon><Plus /></el-icon>
              添加请求头
            </el-button>
          </div>
        </el-form-item>

        <el-alert
          v-if="addError"
          :title="addError"
          type="error"
          show-icon
          :closable="true"
          @close="addError = ''"
          style="margin-bottom: var(--space-3);"
        />
      </el-form>

      <template #footer>
        <el-button @click="showAddDialog = false" :disabled="adding">取消</el-button>
        <el-button type="primary" @click="handleAdd" :loading="adding">
          {{ adding ? '保存中...' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Plus,
  Setting,
  Connection,
  Refresh,
  Delete,
  WarningFilled,
  ArrowLeft
} from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { mcpApi } from '../api/mcp'
import type { McpServerInfo, McpToolInfo, HeaderEntry } from '../types'

const router = useRouter()

// ===== 数据状态 =====
const servers = ref<(McpServerInfo & { tools?: McpToolInfo[] })[]>([])
const loading = ref(false)
const adding = ref(false)
const reconnecting = ref<string | null>(null)
const showAddDialog = ref(false)
const addError = ref('')
const addFormRef = ref()
const dialogTitle = ref('添加 MCP Server')

interface AddFormState {
  name: string
  baseUrl: string
  headerEntries: HeaderEntry[]
}

const addForm = reactive<AddFormState>({
  name: '',
  baseUrl: '',
  headerEntries: [{ key: '', value: '' }]
})

const userId = () => localStorage.getItem('userId') || 'default_user'

const goBack = () => {
  router.push('/chat')
}

// ===== Header 辅助方法 =====

/** 判断是否为敏感 Key */
const isSensitiveKey = (key: string): boolean => {
  const lower = key.toLowerCase()
  return lower.includes('key') || lower.includes('token') ||
    lower.includes('secret') || lower.includes('password') ||
    lower.includes('auth') || lower.includes('api')
}

/** 根据 Key 返回 placeholder 提示 */
const getValuePlaceholder = (key: string): string => {
  const lower = key.toLowerCase()
  if (lower === 'authorization') {
    if (addForm.headerEntries.find(h => h.key === key)?.value?.startsWith('Bearer ')) {
      return 'Bearer eyJhbGciOi...'
    }
    if (addForm.headerEntries.find(h => h.key === key)?.value?.startsWith('Basic ')) {
      return 'Basic base64(user:pass)'
    }
    return 'Bearer <token> 或 Basic <credentials>'
  }
  if (lower.includes('api') && lower.includes('key')) return 'sk-xxxxxxxx'
  if (lower.includes('token')) return '<token>'
  return 'Header Value'
}

/** Header 条目变化后同步到 headers 对象（Map<string, string>） */
const buildHeadersMap = (): Record<string, string> => {
  const map: Record<string, string> = {}
  for (const entry of addForm.headerEntries) {
    if (entry.key.trim() && entry.value.trim()) {
      map[entry.key.trim()] = entry.value.trim()
    }
  }
  return map
}

/** 添加空白 Header 行 */
const addHeader = () => {
  addForm.headerEntries.push({ key: '', value: '' })
}

/** 移除指定 Header 行 */
const removeHeader = (index: number) => {
  if (addForm.headerEntries.length > 1) {
    addForm.headerEntries.splice(index, 1)
  }
}

/** 应用预设鉴权模板 */
const applyPreset = (type: 'bearer' | 'apikey' | 'basic') => {
  // 删除空行，仅保留有效 header
  addForm.headerEntries = addForm.headerEntries.filter(e => e.key.trim() || e.value.trim())
  if (addForm.headerEntries.length === 0 || addForm.headerEntries[addForm.headerEntries.length - 1].key.trim()) {
    addForm.headerEntries.push({ key: '', value: '' })
  }

  switch (type) {
    case 'bearer':
      addForm.headerEntries = [
        ...addForm.headerEntries.filter(e => e.key.toLowerCase() !== 'authorization'),
        { key: 'Authorization', value: 'Bearer ' }
      ]
      break
    case 'apikey':
      addForm.headerEntries = [
        ...addForm.headerEntries.filter(e => !e.key.toLowerCase().includes('api') && !e.key.toLowerCase().includes('key')),
        { key: 'X-API-Key', value: '' }
      ]
      break
    case 'basic':
      addForm.headerEntries = [
        ...addForm.headerEntries.filter(e => e.key.toLowerCase() !== 'authorization'),
        { key: 'Authorization', value: 'Basic ' }
      ]
      break
  }
}

/** 脱敏展示 header 值：保留前4后4字符，中间用 *** 替换 */
const maskValue = (value: string): string => {
  if (!value) return ''
  if (value.length <= 8) return '****'
  return value.substring(0, 4) + '***' + value.substring(value.length - 4)
}

// ===== 加载服务器列表 =====
const loadServers = async () => {
  loading.value = true
  try {
    const res = await mcpApi.listServers({ userId: userId() })
    if (res.code === 1 && res.data) {
      servers.value = res.data.map(s => ({ ...s, tools: [] }))
      await loadServerTools()
    } else {
      servers.value = []
    }
  } catch {
    servers.value = []
  } finally {
    loading.value = false
  }
}

/** 加载所有已连接 Server 的工具列表 */
const loadServerTools = async () => {
  try {
    const res = await mcpApi.getMcpTools({ userId: userId() })
    if (res.code === 1 && res.data) {
      for (const group of res.data) {
        const server = servers.value.find(s => s.name === group.serverName)
        if (server) {
          server.tools = group.tools
        }
      }
    }
  } catch {
    // 静默失败
  }
}

// ===== 打开添加对话框 =====
const openAddDialog = () => {
  dialogTitle.value = '添加 MCP Server'
  showAddDialog.value = true
}

// ===== 添加 Server =====
const handleAdd = async () => {
  if (!addForm.name.trim() || !addForm.baseUrl.trim()) {
    ElMessage.warning('请填写完整信息')
    return
  }

  adding.value = true
  addError.value = ''
  try {
    const res = await mcpApi.addServer({
      userId: userId(),
      name: addForm.name.trim(),
      baseUrl: addForm.baseUrl.trim(),
      headers: buildHeadersMap()
    })

    if (res.code === 1) {
      ElMessage.success('MCP Server 添加成功')
      showAddDialog.value = false
      await loadServers()
    } else {
      addError.value = res.msg || '保存失败'
    }
  } catch (error: any) {
    addError.value = error?.msg || error?.message || '保存失败'
  } finally {
    adding.value = false
  }
}

// ===== 重连 Server =====
const handleReconnect = async (serverName: string) => {
  reconnecting.value = serverName
  try {
    const res = await mcpApi.reconnectServer(serverName, { userId: userId() })
    if (res.code === 1) {
      ElMessage.success(`已重新连接到 ${serverName}`)
      await loadServers()
    } else {
      ElMessage.error(res.msg || '重连失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.msg || '重连失败')
  } finally {
    reconnecting.value = null
  }
}

// ===== 删除 Server =====
const handleDelete = async (serverName: string) => {
  try {
    const res = await mcpApi.deleteServer(serverName, { userId: userId() })
    if (res.code === 1) {
      ElMessage.success(`已删除 ${serverName}`)
      servers.value = servers.value.filter(s => s.name !== serverName)
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.msg || '删除失败')
  }
}

// ===== 表单重置 =====
const resetAddForm = () => {
  addForm.name = ''
  addForm.baseUrl = ''
  addForm.headerEntries = [{ key: '', value: '' }]
  addError.value = ''
  addFormRef.value?.resetFields()
}

onMounted(() => {
  loadServers()
})
</script>

<style scoped>
.mcp-servers-page {
  max-width: 820px;
  margin: 0 auto;
  padding: var(--space-8) var(--space-6);
}

/* ===== Page Header ===== */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--space-6);
  flex-wrap: wrap;
  gap: var(--space-4);
}

.back-btn {
  margin-bottom: var(--space-2);
}

.header-left h2 {
  margin: 0 0 var(--space-1) 0;
  font-family: var(--font-heading);
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.header-desc {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

/* ===== Loading & Empty ===== */
.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-10) var(--space-4);
  color: var(--color-text-tertiary);
  gap: var(--space-3);
}

.empty-state p {
  margin: 0;
  font-size: var(--font-size-base);
}

/* ===== Server List ===== */
.server-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

/* ===== Server Card ===== */
.server-card {
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  overflow: hidden;
  transition: border-color var(--transition-fast),
              box-shadow var(--transition-fast);
}

.server-card:hover {
  border-color: var(--color-border);
  box-shadow: var(--shadow-sm);
}

.server-card.is-disconnected {
  border-color: #ffcdd2;
  background: #fff5f5;
}

.server-card-header {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-border-light);
}

.server-name-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-1);
}

.server-name {
  font-weight: var(--font-weight-semibold);
  font-family: var(--font-mono);
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
}

.server-url {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
  word-break: break-all;
}

/* ===== Connection Dot ===== */
.connection-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f44336;
}

.connection-dot.connected {
  background: #4caf50;
  box-shadow: 0 0 4px rgba(76, 175, 80, 0.5);
}

/* ===== Card Body ===== */
.server-card-body {
  padding: var(--space-4) var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

/* ===== Auth Headers in Card ===== */
.server-auth {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.auth-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-medium);
}

.auth-tag {
  font-family: var(--font-mono);
  font-size: 11px;
}

.disconnected-hint {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
  color: #d32f2f;
}

.server-tools {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.tools-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.tool-tag {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
}

.no-tools-hint {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  font-style: italic;
}

/* ===== Card Actions ===== */
.server-card-actions {
  padding: var(--space-3) var(--space-5);
  border-top: 1px solid var(--color-border-light);
  display: flex;
  gap: var(--space-2);
  justify-content: flex-end;
}

/* ===== Dialog: Headers Section ===== */
.headers-section {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.header-presets {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  margin-bottom: var(--space-1);
  flex-wrap: wrap;
}

.presets-label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-right: var(--space-1);
}

.header-entry {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.header-key-input {
  flex: 0 0 160px;
}

.header-value-input {
  flex: 1;
}

.add-header-btn {
  align-self: flex-start;
  font-size: var(--font-size-xs);
}
</style>
