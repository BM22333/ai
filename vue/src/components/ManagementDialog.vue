<template>
  <el-dialog
    v-model="dialogVisible"
    title="管理"
    width="780px"
    :close-on-click-modal="false"
    @open="onOpen"
  >
    <el-tabs v-model="activeTab" type="border-card">
      <!-- ===== Tab 1: API 配置 ===== -->
      <el-tab-pane label="API 配置" name="api">
        <div v-loading="apiLoading" class="tab-content">
          <div v-if="!apiLoading && apiConfigs.length === 0" class="tab-empty">
            <el-icon :size="32" color="#c0c4cc"><FolderOpened /></el-icon>
            <p>暂无 API 配置</p>
          </div>
          <el-table v-else :data="apiConfigs" size="small" stripe style="width: 100%">
            <el-table-column prop="configName" label="名称" min-width="100" />
            <el-table-column prop="modelName" label="模型" min-width="140">
              <template #default="{ row }"><el-tag type="primary" effect="light" size="small">{{ row.modelName }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="baseUrl" label="Base URL" min-width="150">
              <template #default="{ row }"><span class="url-cell">{{ row.baseUrl }}</span></template>
            </el-table-column>
            <el-table-column label="API Key" width="120">
              <template #default="{ row }"><code class="key-cell">{{ maskKey(row.apiKey) }}</code></template>
            </el-table-column>
            <el-table-column label="操作" width="110" align="center">
              <template #default="{ row }">
                <el-button text size="small" type="primary" @click="apiStartEdit(row)"><el-icon><Edit /></el-icon></el-button>
                <el-button text size="small" type="danger" @click="apiDelete(row)"><el-icon><Delete /></el-icon></el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button type="primary" size="small" style="margin-top: var(--space-3)" @click="apiStartAdd" :disabled="apiShowForm">
            <el-icon><Plus /></el-icon>添加配置
          </el-button>
          <transition name="slide-fade">
            <div v-if="apiShowForm" class="tab-form">
              <el-divider />
              <div class="form-title">{{ apiEditingId ? '编辑配置' : '添加新配置' }}</div>
              <el-form :model="apiForm" label-width="90px" size="small">
                <el-form-item label="名称" required><el-input v-model="apiForm.configName" placeholder="如：我的DeepSeek" maxlength="30" /></el-form-item>
                <el-form-item label="API Key" required><el-input v-model="apiForm.apiKey" type="password" placeholder="sk-xxx" show-password /></el-form-item>
                <el-form-item label="模型"><div class="inline-row"><el-input v-model="apiForm.modelName" placeholder="输入或获取列表" class="flex-1" /><el-button size="small" :loading="apiFetching" @click="apiFetchModels">获取</el-button></div>
                  <div v-if="apiModels.length" class="model-list">
                    <div v-for="m in apiModels" :key="m.id" class="model-item" :class="{selected: apiForm.modelName===m.id}" @click="apiForm.modelName=m.id">
                      <el-icon v-if="apiForm.modelName===m.id" color="#1890ff" :size="16"><SuccessFilled /></el-icon>
                      <span v-else class="dot-empty"></span>
                      <span class="m-id">{{ m.id }}</span>
                    </div>
                  </div>
                </el-form-item>
                <el-form-item label="Base URL" required><el-input v-model="apiForm.baseUrl" placeholder="https://api.siliconflow.cn" /></el-form-item>
              </el-form>
              <div class="form-actions">
                <el-button type="primary" size="small" :loading="apiSaving" @click="apiSave">{{ apiEditingId ? '更新' : '保存' }}</el-button>
                <el-button size="small" :loading="apiTesting" @click="apiTest">测试</el-button>
                <el-button size="small" @click="apiCancel">取消</el-button>
              </div>
            </div>
          </transition>
        </div>
      </el-tab-pane>

      <!-- ===== Tab 2: MCP Server ===== -->
      <el-tab-pane label="MCP Server" name="mcp">
        <div v-loading="mcpLoading" class="tab-content">
          <div v-if="!mcpLoading && mcpServers.length === 0" class="tab-empty">
            <el-icon :size="32" color="#909399"><Connection /></el-icon>
            <p>暂无 MCP Server</p>
          </div>
          <div v-else class="server-list">
            <div v-for="s in mcpServers" :key="s.name" class="server-card" :class="{'is-disconnected': !s.connected}">
              <div class="sc-header">
                <span class="sc-name">{{ s.name }}</span>
                <el-tag :type="s.connected?'success':'danger'" size="small" effect="light">
                  <span class="dot" :class="{on: s.connected}"></span>{{ s.connected?'已连接':'已断开' }}
                </el-tag>
                <span class="sc-url">{{ s.baseUrl }}</span>
              </div>
              <div class="sc-body">
                <div v-if="s.headers && Object.keys(s.headers).length" class="sc-auth">
                  <span class="lbl">鉴权：</span>
                  <el-tag v-for="(v,k) in s.headers" :key="k" size="small" type="warning" effect="light" class="sc-auth-tag">{{ k }}: {{ maskValue(String(v)) }}</el-tag>
                </div>
                <div v-if="s.tools?.length" class="sc-tools">
                  <span class="lbl">工具：</span>
                  <el-tag v-for="t in s.tools" :key="t.fullName" size="small" type="info" effect="light" class="sc-tool-tag">{{ t.displayName }}</el-tag>
                </div>
              </div>
              <div class="sc-actions">
                <el-button text size="small" type="primary" :loading="mcpReconnecting===s.name" @click="mcpReconnect(s.name)"><el-icon><Refresh /></el-icon>重连</el-button>
                <el-popconfirm title="确定删除？" @confirm="mcpDelete(s.name)"><template #reference><el-button text size="small" type="danger"><el-icon><Delete /></el-icon>删除</el-button></template></el-popconfirm>
              </div>
            </div>
          </div>
          <el-divider />
          <div class="add-section">
            <div class="form-title">添加 MCP Server</div>
            <el-form :model="mcpForm" inline size="small">
              <el-form-item label="名称" required><el-input v-model="mcpForm.name" placeholder="weather" style="width:120px" /></el-form-item>
              <el-form-item label="URL" required><el-input v-model="mcpForm.baseUrl" placeholder="https://..." style="width:200px" /></el-form-item>
              <el-form-item><el-button type="primary" :loading="mcpAdding" @click="mcpAdd">添加</el-button></el-form-item>
            </el-form>
            <div class="headers-section">
              <div class="header-presets">
                <span class="presets-lbl">鉴权：</span>
                <el-button size="small" text type="primary" @click="mcpPreset('bearer')">Bearer</el-button>
                <el-button size="small" text type="primary" @click="mcpPreset('apikey')">API Key</el-button>
                <el-button size="small" text type="primary" @click="mcpPreset('basic')">Basic</el-button>
                <el-button size="small" text @click="mcpAddHeader"><el-icon><Plus /></el-icon>请求头</el-button>
              </div>
              <div v-for="(h,i) in mcpHeaders" :key="i" class="header-entry">
                <el-input v-model="h.key" placeholder="Key" size="small" style="width:130px" />
                <el-input v-model="h.value" placeholder="Value" size="small" style="width:180px" :type="isSensitive(h.key)?'password':'text'" show-password />
                <el-button text size="small" type="danger" @click="mcpRemoveHeader(i)" :disabled="mcpHeaders.length<=1"><el-icon><Delete /></el-icon></el-button>
              </div>
            </div>
            <el-alert v-if="mcpError" :title="mcpError" type="error" show-icon :closable="true" @close="mcpError=''" style="margin-top:var(--space-2)" />
          </div>
        </div>
      </el-tab-pane>

      <!-- ===== Tab 3: 注入提示词 ===== -->
      <el-tab-pane label="注入提示词" name="insert">
        <div v-loading="insertLoading" class="tab-content">
          <div v-if="!insertLoading && insertPrompts.length === 0" class="tab-empty">
            <p>暂无注入提示词</p>
          </div>
          <el-table v-else :data="insertPrompts" size="small" stripe style="width:100%">
            <el-table-column prop="promptName" label="名称" min-width="120" />
            <el-table-column prop="depth" label="深度" width="80" align="center">
              <template #default="{row}"><el-tag size="small" type="info">D{{ row.depth }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="content" label="内容" min-width="200">
              <template #default="{row}"><span class="preview">{{ truncate(row.content, 50) }}</span></template>
            </el-table-column>
            <el-table-column label="操作" width="110" align="center">
              <template #default="{row}">
                <el-button text size="small" type="primary" @click="insertStartEdit(row)"><el-icon><Edit /></el-icon></el-button>
                <el-button text size="small" type="danger" @click="insertDelete(row.id)"><el-icon><Delete /></el-icon></el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button type="primary" size="small" style="margin-top:var(--space-3)" @click="insertStartAdd" :disabled="insertShowForm">
            <el-icon><Plus /></el-icon>新建提示词
          </el-button>
          <transition name="slide-fade">
            <div v-if="insertShowForm" class="tab-form">
              <el-divider />
              <div class="form-title">{{ insertEditingId ? '编辑提示词' : '新建提示词' }}</div>
              <el-form :model="insertForm" label-width="80px" size="small">
                <el-form-item label="名称" required><el-input v-model="insertForm.promptName" maxlength="50" /></el-form-item>
                <el-form-item label="深度" required><el-input-number v-model="insertForm.depth" :min="0" style="width:100%" /></el-form-item>
                <el-form-item label="内容" required><el-input v-model="insertForm.content" type="textarea" :rows="3" /></el-form-item>
              </el-form>
              <div class="form-actions">
                <el-button type="primary" size="small" :loading="insertSaving" @click="insertSave">{{ insertEditingId ? '更新' : '保存' }}</el-button>
                <el-button size="small" @click="insertCancel">取消</el-button>
              </div>
            </div>
          </transition>
        </div>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="dialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, FolderOpened, Connection, Refresh, SuccessFilled } from '@element-plus/icons-vue'
import { userApi } from '../api/user'
import { mcpApi } from '../api/mcp'
import { insertPromptApi } from '../api/insertPrompt'
import type { UserApi, ModelInfo, McpServerInfo, McpToolInfo, InsertPrompt, HeaderEntry } from '../types'

const dialogVisible = ref(false)
const activeTab = ref('api')
const uid = () => localStorage.getItem('userId') || 'default_user'
const truncate = (t: string, n: number) => t?.length > n ? t.substring(0, n) + '...' : t

// ==================== API 配置 ====================
const apiLoading = ref(false)
const apiSaving = ref(false)
const apiTesting = ref(false)
const apiFetching = ref(false)
const apiShowForm = ref(false)
const apiEditingId = ref<number | null>(null)
const apiConfigs = ref<UserApi[]>([])
const apiModels = ref<ModelInfo[]>([])

const apiForm = reactive({ configName: '', apiKey: '', modelName: '', baseUrl: 'https://api.siliconflow.cn' })

const maskKey = (k: string) => k?.length > 8 ? k.substring(0, 4) + '****' + k.substring(k.length - 4) : '****'

const apiLoad = async () => {
  apiLoading.value = true
  try { const r = await userApi.getApiConfigs({ userId: uid() }); apiConfigs.value = r.code === 1 && r.data ? r.data : [] } catch { apiConfigs.value = [] }
  finally { apiLoading.value = false }
}
const apiResetForm = () => { apiForm.configName = ''; apiForm.apiKey = ''; apiForm.modelName = ''; apiForm.baseUrl = 'https://api.siliconflow.cn'; apiModels.value = [] }
const apiStartAdd = () => { apiEditingId.value = null; apiResetForm(); apiShowForm.value = true }
const apiStartEdit = (c: UserApi) => { apiEditingId.value = c.id; apiForm.configName = c.configName; apiForm.apiKey = c.apiKey; apiForm.modelName = c.modelName; apiForm.baseUrl = c.baseUrl; apiModels.value = []; apiShowForm.value = true }
const apiCancel = () => { apiShowForm.value = false; apiEditingId.value = null; apiResetForm() }
const apiFetchModels = async () => {
  if (!apiForm.apiKey || !apiForm.baseUrl) { ElMessage.warning('请填写 API Key 和 Base URL'); return }
  apiFetching.value = true; apiModels.value = []
  try { const r = await userApi.getModels({ apiKey: apiForm.apiKey, baseUrl: apiForm.baseUrl }); if (r.code === 1 && r.data) apiModels.value = r.data; else ElMessage.warning('未获取到模型') } catch (e: any) { ElMessage.error(e?.msg || '请求失败') }
  finally { apiFetching.value = false }
}
const apiSave = async () => {
  if (!apiForm.configName.trim() || !apiForm.apiKey.trim() || !apiForm.modelName.trim() || !apiForm.baseUrl.trim()) { ElMessage.warning('请填写完整'); return }
  apiSaving.value = true
  try {
    const p = { configName: apiForm.configName, apiKey: apiForm.apiKey, modelName: apiForm.modelName, baseUrl: apiForm.baseUrl, userId: uid() }
    const r = apiEditingId.value ? await userApi.updateConfig({ id: apiEditingId.value, ...p }) : await userApi.saveConfig(p)
    if (r.code === 1) { ElMessage.success(apiEditingId.value ? '已更新' : '已保存'); apiCancel(); await apiLoad() }
    else ElMessage.error(r.msg || '保存失败')
  } catch (e: any) { ElMessage.error(e?.msg || '保存失败') }
  finally { apiSaving.value = false }
}
const apiTest = async () => {
  if (!apiForm.apiKey || !apiForm.baseUrl) { ElMessage.warning('请填写 API Key 和 Base URL'); return }
  apiTesting.value = true
  try { const r = await userApi.getModels({ apiKey: apiForm.apiKey, baseUrl: apiForm.baseUrl }); r.code === 1 ? ElMessage.success('测试通过') : ElMessage.error(r.msg || '测试失败') } catch (e: any) { ElMessage.error(e?.msg || '测试失败') }
  finally { apiTesting.value = false }
}
const apiDelete = async (c: UserApi) => {
  try { await ElMessageBox.confirm(`删除"${c.configName}"？`, '确认', { type: 'warning' }); await userApi.deleteConfig({ id: c.id, userId: uid() }); ElMessage.success('已删除'); if (apiEditingId.value === c.id) apiCancel(); await apiLoad() } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.msg || '删除失败') }
}

// ==================== MCP Server ====================
const mcpLoading = ref(false)
const mcpAdding = ref(false)
const mcpReconnecting = ref<string | null>(null)
const mcpServers = ref<(McpServerInfo & { tools?: McpToolInfo[] })[]>([])
const mcpError = ref('')
const mcpForm = reactive({ name: '', baseUrl: '' })
const mcpHeaders = ref<HeaderEntry[]>([{ key: '', value: '' }])

const mcpLoad = async () => {
  mcpLoading.value = true
  try {
    const r = await mcpApi.listServers({ userId: uid() })
    mcpServers.value = r.code === 1 && r.data ? r.data.map((s: McpServerInfo) => ({ ...s, tools: [] })) : []
    const tr = await mcpApi.getMcpTools({ userId: uid() })
    if (tr.code === 1 && tr.data) for (const g of tr.data) { const s = mcpServers.value.find(s => s.name === g.serverName); if (s) s.tools = g.tools }
  } catch { mcpServers.value = [] }
  finally { mcpLoading.value = false }
}
const maskValue = (v: string) => v?.length > 8 ? v.substring(0, 4) + '***' + v.substring(v.length - 4) : '****'
const isSensitive = (k: string) => ['key', 'token', 'secret', 'password', 'auth', 'api'].some(w => k.toLowerCase().includes(w))
const mcpBuildHeaders = (): Record<string, string> => { const m: Record<string, string> = {}; for (const e of mcpHeaders.value) if (e.key.trim() && e.value.trim()) m[e.key.trim()] = e.value.trim(); return m }
const mcpAddHeader = () => mcpHeaders.value.push({ key: '', value: '' })
const mcpRemoveHeader = (i: number) => { if (mcpHeaders.value.length > 1) mcpHeaders.value.splice(i, 1) }
const mcpPreset = (t: 'bearer' | 'apikey' | 'basic') => {
  mcpHeaders.value = mcpHeaders.value.filter(e => e.key.trim() || e.value.trim())
  if (t === 'bearer') mcpHeaders.value.push({ key: 'Authorization', value: 'Bearer ' })
  else if (t === 'apikey') mcpHeaders.value.push({ key: 'X-API-Key', value: '' })
  else mcpHeaders.value.push({ key: 'Authorization', value: 'Basic ' })
}
const mcpResetForm = () => { mcpForm.name = ''; mcpForm.baseUrl = ''; mcpHeaders.value = [{ key: '', value: '' }]; mcpError.value = '' }
const mcpAdd = async () => {
  if (!mcpForm.name.trim() || !mcpForm.baseUrl.trim()) { ElMessage.warning('请填写完整'); return }
  mcpAdding.value = true; mcpError.value = ''
  try { const r = await mcpApi.addServer({ userId: uid(), name: mcpForm.name.trim(), baseUrl: mcpForm.baseUrl.trim(), headers: mcpBuildHeaders() }); if (r.code === 1) { ElMessage.success('已添加'); mcpResetForm(); await mcpLoad() } else mcpError.value = r.msg || '添加失败' } catch (e: any) { mcpError.value = e?.msg || '添加失败' }
  finally { mcpAdding.value = false }
}
const mcpReconnect = async (n: string) => { mcpReconnecting.value = n; try { const r = await mcpApi.reconnectServer(n, { userId: uid() }); if (r.code === 1) { ElMessage.success(`已重连 ${n}`); await mcpLoad() } else ElMessage.error(r.msg || '失败') } catch (e: any) { ElMessage.error(e?.msg || '失败') } finally { mcpReconnecting.value = null } }
const mcpDelete = async (n: string) => { try { const r = await mcpApi.deleteServer(n, { userId: uid() }); if (r.code === 1) { ElMessage.success(`已删除 ${n}`); mcpServers.value = mcpServers.value.filter(s => s.name !== n) } else ElMessage.error(r.msg || '失败') } catch (e: any) { ElMessage.error(e?.msg || '失败') } }

// ==================== 注入提示词 ====================
const insertLoading = ref(false)
const insertSaving = ref(false)
const insertShowForm = ref(false)
const insertEditingId = ref<number | null>(null)
const insertPrompts = ref<InsertPrompt[]>([])
const insertForm = reactive({ promptName: '', content: '', depth: 0 })

const insertLoad = async () => {
  insertLoading.value = true
  try { const r = await insertPromptApi.list(uid()); if (r.code === 1 && r.data) insertPrompts.value = r.data } catch { insertPrompts.value = [] }
  finally { insertLoading.value = false }
}
const insertResetForm = () => { insertForm.promptName = ''; insertForm.content = ''; insertForm.depth = 0 }
const insertStartAdd = () => { insertEditingId.value = null; insertResetForm(); insertShowForm.value = true }
const insertStartEdit = (p: InsertPrompt) => { insertEditingId.value = p.id; insertForm.promptName = p.promptName; insertForm.content = p.content; insertForm.depth = p.depth; insertShowForm.value = true }
const insertCancel = () => { insertShowForm.value = false; insertEditingId.value = null; insertResetForm() }
const insertSave = async () => {
  if (!insertForm.promptName.trim() || !insertForm.content.trim()) { ElMessage.warning('请填写完整'); return }
  insertSaving.value = true
  try {
    if (insertEditingId.value) {
      const r = await insertPromptApi.update({ id: insertEditingId.value, userId: uid(), ...insertForm })
      if (r.code === 1) { ElMessage.success('已更新'); insertCancel(); await insertLoad() } else ElMessage.error(r.msg || '失败')
    } else {
      const r = await insertPromptApi.create({ userId: uid(), promptName: insertForm.promptName, content: insertForm.content, depth: insertForm.depth })
      if (r.code === 1) { ElMessage.success('已创建'); insertCancel(); await insertLoad() } else ElMessage.error(r.msg || '失败')
    }
  } catch (e: any) { ElMessage.error(e?.msg || '失败') }
  finally { insertSaving.value = false }
}
const insertDelete = async (id: number) => {
  try { await ElMessageBox.confirm('确定删除？', '确认', { type: 'warning' }); await insertPromptApi.delete(id, uid()); ElMessage.success('已删除'); await insertLoad() } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.msg || '失败') }
}

// ==================== 生命周期 ====================
const onOpen = () => { apiLoad(); mcpLoad(); insertLoad() }

const open = () => {
  apiShowForm.value = false; apiEditingId.value = null; apiResetForm()
  mcpResetForm()
  insertShowForm.value = false; insertEditingId.value = null; insertResetForm()
  activeTab.value = 'api'
  dialogVisible.value = true
}

defineExpose({ open })
</script>

<style scoped>
.tab-content { min-height: 240px; }
.tab-empty { display: flex; flex-direction: column; align-items: center; padding: var(--space-8); color: var(--color-text-tertiary); font-size: var(--font-size-sm); gap: var(--space-2); }
.tab-form { margin-top: var(--space-2); }
.form-title { font-size: var(--font-size-sm); font-weight: var(--font-weight-semibold); margin-bottom: var(--space-3); color: var(--color-text-primary); }
.form-actions { display: flex; gap: var(--space-2); justify-content: flex-end; margin-top: var(--space-2); }

/* API */
.url-cell { color: var(--color-text-secondary); font-size: var(--font-size-xs); word-break: break-all; }
.key-cell { font-family: var(--font-mono); font-size: var(--font-size-xs); color: var(--color-text-tertiary); background: var(--color-bg-page); padding: 2px 6px; border-radius: var(--radius-sm); }
.inline-row { display: flex; gap: var(--space-2); width: 100%; }
.flex-1 { flex: 1; }
.model-list { max-height: 150px; overflow-y: auto; border: 1px solid var(--color-border-light); border-radius: var(--radius-md); margin-top: var(--space-1); }
.model-item { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-1) var(--space-3); cursor: pointer; border-bottom: 1px solid var(--color-border-light); font-size: var(--font-size-sm); }
.model-item:last-child { border-bottom: none; }
.model-item:hover { background: var(--color-bg-page); }
.model-item.selected { background: var(--color-primary-light); }
.m-id { font-weight: var(--font-weight-medium); }
.dot-empty { width: 14px; height: 14px; border: 2px solid var(--color-border); border-radius: 50%; flex-shrink: 0; }

/* MCP */
.server-list { display: flex; flex-direction: column; gap: var(--space-2); max-height: 260px; overflow-y: auto; margin-bottom: var(--space-2); }
.server-card { border: 1px solid var(--color-border-light); border-radius: var(--radius-md); overflow: hidden; background: var(--color-bg-surface); }
.server-card.is-disconnected { border-color: #ffcdd2; background: #fff5f5; }
.sc-header { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3); border-bottom: 1px solid var(--color-border-light); flex-wrap: wrap; }
.sc-name { font-weight: var(--font-weight-semibold); font-family: var(--font-mono); font-size: var(--font-size-sm); }
.sc-url { font-size: var(--font-size-xs); color: var(--color-text-tertiary); width: 100%; font-family: var(--font-mono); word-break: break-all; }
.sc-body { padding: var(--space-2) var(--space-3); display: flex; flex-direction: column; gap: var(--space-1); }
.sc-auth, .sc-tools { display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap; }
.lbl { font-size: var(--font-size-xs); color: var(--color-text-secondary); }
.sc-auth-tag { font-family: var(--font-mono); font-size: 11px; }
.sc-tool-tag { font-size: 11px; }
.sc-actions { padding: var(--space-1) var(--space-3); border-top: 1px solid var(--color-border-light); display: flex; gap: var(--space-1); justify-content: flex-end; }
.dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #f44336; }
.dot.on { background: #4caf50; box-shadow: 0 0 3px rgba(76,175,80,0.5); }

.add-section { margin-top: var(--space-1); }
.header-presets { display: flex; align-items: center; gap: var(--space-1); flex-wrap: wrap; margin-bottom: var(--space-2); }
.presets-lbl { font-size: var(--font-size-xs); color: var(--color-text-tertiary); }
.header-entry { display: flex; align-items: center; gap: var(--space-2); margin-top: var(--space-1); }

/* Insert */
.preview { color: var(--color-text-secondary); font-size: var(--font-size-xs); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; display: block; max-width: 100%; }

/* Transition */
.slide-fade-enter-active { transition: all 0.3s ease; }
.slide-fade-leave-active { transition: all 0.2s ease-in; }
.slide-fade-enter-from, .slide-fade-leave-to { opacity: 0; transform: translateY(-8px); }
</style>
