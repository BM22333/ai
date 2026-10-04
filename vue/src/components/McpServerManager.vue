<template>
  <el-dialog
    v-model="dialogVisible"
    title="MCP Server 管理"
    width="680px"
    :close-on-click-modal="false"
    @open="onOpen"
  >
    <!-- 加载态 -->
    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading" :size="28"><Setting /></el-icon>
      <span>加载中...</span>
    </div>

    <!-- 空态 -->
    <div v-else-if="servers.length === 0" class="empty-state">
      <el-icon :size="40" color="#909399"><Connection /></el-icon>
      <p>暂无 MCP Server</p>
    </div>

    <!-- 服务器列表 -->
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
            <el-tag :type="server.connected ? 'success' : 'danger'" size="small" effect="light">
              <span class="conn-dot" :class="{ connected: server.connected }"></span>
              {{ server.connected ? '已连接' : '已断开' }}
            </el-tag>
          </div>
          <div class="server-url">{{ server.baseUrl }}</div>
        </div>
        <div class="server-card-body">
          <div v-if="server.headers && Object.keys(server.headers).length > 0" class="server-auth">
            <span class="auth-label">鉴权：</span>
            <el-tag v-for="(v, k) in server.headers" :key="k" size="small" type="warning" effect="light" class="auth-tag">{{ k }}: {{ maskValue(String(v)) }}</el-tag>
          </div>
          <div v-if="!server.connected" class="disconnected-hint">
            <el-icon><WarningFilled /></el-icon>连接已断开
          </div>
          <div v-if="server.tools && server.tools.length > 0" class="server-tools">
            <span class="tools-label">可用工具：</span>
            <el-tag v-for="t in server.tools" :key="t.fullName" size="small" type="info" effect="light" class="tool-tag">{{ t.displayName }}</el-tag>
          </div>
        </div>
        <div class="server-card-actions">
          <el-button text size="small" type="primary" :loading="reconnecting === server.name" @click="handleReconnect(server.name)"><el-icon><Refresh /></el-icon>重连</el-button>
          <el-popconfirm title="确定删除此 Server？" @confirm="handleDelete(server.name)">
            <template #reference>
              <el-button text size="small" type="danger"><el-icon><Delete /></el-icon>删除</el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>
    </div>

    <!-- 添加对话框（内嵌） -->
    <el-divider />
    <div class="add-section">
      <div class="add-section-title">
        <el-icon><Plus /></el-icon>
        <span>{{ editingServer ? '编辑 Server' : '添加 MCP Server' }}</span>
      </div>
      <el-form :model="addForm" ref="addFormRef" label-width="100px" inline class="add-form">
        <el-form-item label="名称" prop="name" :rules="[{ required: true, message: '请输入' }]">
          <el-input v-model="addForm.name" placeholder="如 weather" size="small" style="width: 140px" :disabled="!!editingServer" />
        </el-form-item>
        <el-form-item label="端点 URL" prop="baseUrl" :rules="[{ required: true, message: '请输入' }]">
          <el-input v-model="addForm.baseUrl" placeholder="https://..." size="small" style="width: 220px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="small" :loading="adding" @click="handleAdd">{{ editingServer ? '更新' : '添加' }}</el-button>
          <el-button size="small" @click="cancelAdd" v-if="editingServer">取消</el-button>
        </el-form-item>
      </el-form>
      <!-- 请求头 -->
      <div class="headers-section">
        <div class="header-presets">
          <span class="presets-label">鉴权预设：</span>
          <el-button size="small" text type="primary" @click="applyPreset('bearer')">Bearer</el-button>
          <el-button size="small" text type="primary" @click="applyPreset('apikey')">API Key</el-button>
          <el-button size="small" text type="primary" @click="applyPreset('basic')">Basic</el-button>
          <el-button size="small" text @click="addHeader"><el-icon><Plus /></el-icon>添加请求头</el-button>
        </div>
        <div v-for="(h, i) in addForm.headerEntries" :key="i" class="header-entry">
          <el-input v-model="h.key" placeholder="Key" size="small" style="width: 140px" />
          <el-input v-model="h.value" placeholder="Value" size="small" style="width: 200px" :type="isSensitiveKey(h.key) ? 'password' : 'text'" show-password />
          <el-button text size="small" type="danger" @click="removeHeader(i)" :disabled="addForm.headerEntries.length <= 1"><el-icon><Delete /></el-icon></el-button>
        </div>
      </div>
      <el-alert v-if="addError" :title="addError" type="error" show-icon :closable="true" @close="addError = ''" style="margin-top: var(--space-2)" />
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Setting, Connection, Refresh, Delete, WarningFilled } from '@element-plus/icons-vue'
import { mcpApi } from '../api/mcp'
import type { McpServerInfo, McpToolInfo, HeaderEntry } from '../types'

const dialogVisible = ref(false)
const loading = ref(false)
const adding = ref(false)
const reconnecting = ref<string | null>(null)
const servers = ref<(McpServerInfo & { tools?: McpToolInfo[] })[]>([])
const editingServer = ref<string | null>(null)
const addError = ref('')
const addFormRef = ref()

interface AddFormState { name: string; baseUrl: string; headerEntries: HeaderEntry[] }
const addForm = reactive<AddFormState>({ name: '', baseUrl: '', headerEntries: [{ key: '', value: '' }] })

const uid = () => localStorage.getItem('userId') || 'default_user'

const maskValue = (v: string) => v?.length > 8 ? v.substring(0, 4) + '***' + v.substring(v.length - 4) : '****'
const isSensitiveKey = (k: string) => ['key', 'token', 'secret', 'password', 'auth', 'api'].some(w => k.toLowerCase().includes(w))

const buildHeaders = (): Record<string, string> => {
  const m: Record<string, string> = {}
  for (const e of addForm.headerEntries) { if (e.key.trim() && e.value.trim()) m[e.key.trim()] = e.value.trim() }
  return m
}

const addHeader = () => addForm.headerEntries.push({ key: '', value: '' })
const removeHeader = (i: number) => { if (addForm.headerEntries.length > 1) addForm.headerEntries.splice(i, 1) }

const applyPreset = (type: 'bearer' | 'apikey' | 'basic') => {
  addForm.headerEntries = addForm.headerEntries.filter(e => e.key.trim() || e.value.trim())
  switch (type) {
    case 'bearer': addForm.headerEntries.push({ key: 'Authorization', value: 'Bearer ' }); break
    case 'apikey': addForm.headerEntries.push({ key: 'X-API-Key', value: '' }); break
    case 'basic': addForm.headerEntries.push({ key: 'Authorization', value: 'Basic ' }); break
  }
}

const resetAddForm = () => {
  addForm.name = ''; addForm.baseUrl = ''; addForm.headerEntries = [{ key: '', value: '' }]
  editingServer.value = null; addError.value = ''
}

const cancelAdd = () => resetAddForm()

const loadServers = async () => {
  loading.value = true
  try {
    const res = await mcpApi.listServers({ userId: uid() })
    servers.value = (res.code === 1 && res.data ? res.data.map((s: McpServerInfo) => ({ ...s, tools: [] })) : [])
    const toolsRes = await mcpApi.getMcpTools({ userId: uid() })
    if (toolsRes.code === 1 && toolsRes.data) {
      for (const g of toolsRes.data) {
        const s = servers.value.find(s => s.name === g.serverName)
        if (s) s.tools = g.tools
      }
    }
  } catch { servers.value = [] }
  finally { loading.value = false }
}

const handleAdd = async () => {
  if (!addForm.name.trim() || !addForm.baseUrl.trim()) { ElMessage.warning('请填写完整'); return }
  adding.value = true; addError.value = ''
  try {
    const res = await mcpApi.addServer({ userId: uid(), name: addForm.name.trim(), baseUrl: addForm.baseUrl.trim(), headers: buildHeaders() })
    if (res.code === 1) { ElMessage.success('添加成功'); resetAddForm(); await loadServers() }
    else addError.value = res.msg || '添加失败'
  } catch (e: any) { addError.value = e?.msg || '添加失败' }
  finally { adding.value = false }
}

const handleReconnect = async (name: string) => {
  reconnecting.value = name
  try {
    const res = await mcpApi.reconnectServer(name, { userId: uid() })
    if (res.code === 1) { ElMessage.success(`已重连 ${name}`); await loadServers() }
    else ElMessage.error(res.msg || '重连失败')
  } catch (e: any) { ElMessage.error(e?.msg || '重连失败') }
  finally { reconnecting.value = null }
}

const handleDelete = async (name: string) => {
  try {
    const res = await mcpApi.deleteServer(name, { userId: uid() })
    if (res.code === 1) { ElMessage.success(`已删除 ${name}`); servers.value = servers.value.filter(s => s.name !== name) }
    else ElMessage.error(res.msg || '删除失败')
  } catch (e: any) { ElMessage.error(e?.msg || '删除失败') }
}

const onOpen = () => { loadServers() }

const open = () => {
  resetAddForm()
  dialogVisible.value = true
}

defineExpose({ open })
</script>

<style scoped>
.loading-state, .empty-state { display: flex; flex-direction: column; align-items: center; padding: var(--space-8); color: var(--color-text-tertiary); font-size: var(--font-size-sm); gap: var(--space-3); }
.server-list { display: flex; flex-direction: column; gap: var(--space-3); max-height: 300px; overflow-y: auto; }
.server-card { border: 1px solid var(--color-border-light); border-radius: var(--radius-md); background: var(--color-bg-surface); overflow: hidden; }
.server-card.is-disconnected { border-color: #ffcdd2; background: #fff5f5; }
.server-card-header { padding: var(--space-2) var(--space-4); border-bottom: 1px solid var(--color-border-light); }
.server-name-row { display: flex; align-items: center; gap: var(--space-2); }
.server-name { font-weight: var(--font-weight-semibold); font-family: var(--font-mono); font-size: var(--font-size-sm); }
.server-url { font-size: var(--font-size-xs); color: var(--color-text-tertiary); font-family: var(--font-mono); word-break: break-all; margin-top: 2px; }
.server-card-body { padding: var(--space-2) var(--space-4); display: flex; flex-direction: column; gap: var(--space-1); }
.server-auth { display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap; }
.auth-label { font-size: var(--font-size-xs); color: var(--color-text-secondary); }
.auth-tag { font-family: var(--font-mono); font-size: 11px; }
.disconnected-hint { display: flex; align-items: center; gap: var(--space-1); font-size: var(--font-size-xs); color: #d32f2f; }
.server-tools { display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap; }
.tools-label { font-size: var(--font-size-xs); color: var(--color-text-secondary); }
.tool-tag { font-size: 11px; }
.server-card-actions { padding: var(--space-2) var(--space-4); border-top: 1px solid var(--color-border-light); display: flex; gap: var(--space-1); justify-content: flex-end; }
.conn-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #f44336; }
.conn-dot.connected { background: #4caf50; box-shadow: 0 0 3px rgba(76,175,80,0.5); }
.add-section { margin-top: var(--space-2); }
.add-section-title { display: flex; align-items: center; gap: var(--space-2); font-weight: var(--font-weight-semibold); font-size: var(--font-size-sm); margin-bottom: var(--space-2); color: var(--color-text-primary); }
.add-form { display: flex; flex-wrap: wrap; gap: 0 var(--space-4); }
.headers-section { margin-top: var(--space-2); }
.header-presets { display: flex; align-items: center; gap: var(--space-1); flex-wrap: wrap; margin-bottom: var(--space-2); }
.presets-label { font-size: var(--font-size-xs); color: var(--color-text-tertiary); }
.header-entry { display: flex; align-items: center; gap: var(--space-2); margin-top: var(--space-1); }
</style>
