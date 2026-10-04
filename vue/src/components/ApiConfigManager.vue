<template>
  <el-dialog
    v-model="dialogVisible"
    title="API 配置管理"
    width="700px"
    :close-on-click-modal="false"
    @open="onOpen"
  >
    <!-- 配置列表 -->
    <div v-loading="loading">
      <div v-if="!loading && configs.length === 0" class="empty-state">
        <el-icon :size="40" color="#c0c4cc"><FolderOpened /></el-icon>
        <p>暂无 API 配置，点击下方按钮添加</p>
      </div>
      <el-table v-else :data="configs" size="small" stripe style="width: 100%">
        <el-table-column prop="configName" label="配置名称" min-width="120" />
        <el-table-column prop="modelName" label="模型" min-width="160">
          <template #default="{ row }">
            <el-tag type="primary" effect="light" size="small">{{ row.modelName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="Base URL" min-width="160">
          <template #default="{ row }">
            <span class="url-cell">{{ row.baseUrl }}</span>
          </template>
        </el-table-column>
        <el-table-column label="API Key" width="130">
          <template #default="{ row }">
            <code class="key-cell">{{ maskApiKey(row.apiKey) }}</code>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button text size="small" type="primary" @click="startEdit(row)"><el-icon><Edit /></el-icon></el-button>
            <el-button text size="small" type="danger" :loading="deletingId === row.id" @click="handleDelete(row)"><el-icon><Delete /></el-icon></el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 添加/编辑表单 -->
    <el-divider v-if="showForm" />
    <transition name="slide-fade">
      <div v-if="showForm" class="edit-form">
        <div class="edit-form-title">{{ editingId ? '编辑配置' : '添加新配置' }}</div>
        <el-form :model="form" ref="formRef" label-width="100px">
          <el-form-item label="配置名称" prop="configName" :rules="[{ required: true, message: '请输入', trigger: 'blur' }]">
            <el-input v-model="form.configName" placeholder="如：我的DeepSeek" maxlength="30" show-word-limit />
          </el-form-item>
          <el-form-item label="API Key" prop="apiKey" :rules="[{ required: true, message: '请输入', trigger: 'blur' }]">
            <el-input v-model="form.apiKey" type="password" placeholder="sk-xxxxxxxx" show-password />
          </el-form-item>
          <el-form-item label="模型">
            <div class="model-row">
              <el-input v-model="form.modelName" placeholder="输入模型名称，或点击按钮获取列表" class="model-input" clearable />
              <el-button size="small" :loading="fetchingModels" @click="fetchModels">获取模型</el-button>
            </div>
            <!-- 模型列表 -->
            <div v-if="models.length > 0" class="model-list">
              <div
                v-for="m in models"
                :key="m.id"
                class="model-item"
                :class="{ selected: form.modelName === m.id }"
                @click="form.modelName = m.id"
              >
                <el-icon v-if="form.modelName === m.id" color="#1890ff"><SuccessFilled /></el-icon>
                <span v-else class="radio-empty"></span>
                <span class="model-id">{{ m.id }}</span>
                <span class="model-owned">by {{ m.ownedBy }}</span>
              </div>
            </div>
          </el-form-item>
          <el-form-item label="Base URL" prop="baseUrl" :rules="[{ required: true, message: '请输入', trigger: 'blur' }]">
            <el-input v-model="form.baseUrl" placeholder="https://api.siliconflow.cn" />
          </el-form-item>
        </el-form>
        <div class="edit-actions">
          <el-button type="primary" :loading="saving" @click="handleSave">{{ editingId ? '更新' : '保存' }}</el-button>
          <el-button :loading="testing" @click="handleTest">测试连接</el-button>
          <el-button @click="cancelForm">取消</el-button>
        </div>
      </div>
    </transition>

    <template #footer>
      <div style="display: flex; gap: var(--space-2)">
        <el-button type="primary" @click="startAdd" :disabled="showForm">
          <el-icon><Plus /></el-icon>
          添加配置
        </el-button>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus, Edit, Delete, FolderOpened, SuccessFilled } from '@element-plus/icons-vue'
import { userApi } from '../api/user'
import type { UserApi, ModelInfo } from '../types'

const dialogVisible = ref(false)
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const fetchingModels = ref(false)
const deletingId = ref<number | null>(null)
const showForm = ref(false)
const editingId = ref<number | null>(null)
const configs = ref<UserApi[]>([])
const models = ref<ModelInfo[]>([])
const formRef = ref<FormInstance>()

const form = reactive({
  configName: '',
  apiKey: '',
  modelName: '',
  baseUrl: 'https://api.siliconflow.cn'
})

const userId = () => localStorage.getItem('userId') || 'default_user'

const maskApiKey = (key: string) => {
  if (!key) return '****'
  if (key.length <= 8) return '****'
  return key.substring(0, 4) + '****' + key.substring(key.length - 4)
}

const loadConfigs = async () => {
  loading.value = true
  try {
    const res = await userApi.getApiConfigs({ userId: userId() })
    configs.value = res.code === 1 && res.data ? res.data : []
  } catch { configs.value = [] }
  finally { loading.value = false }
}

const resetForm = () => {
  form.configName = ''
  form.apiKey = ''
  form.modelName = ''
  form.baseUrl = 'https://api.siliconflow.cn'
  models.value = []
  formRef.value?.clearValidate()
}

const startAdd = () => {
  editingId.value = null
  resetForm()
  showForm.value = true
}

const startEdit = (config: UserApi) => {
  editingId.value = config.id
  form.configName = config.configName
  form.apiKey = config.apiKey
  form.modelName = config.modelName
  form.baseUrl = config.baseUrl
  models.value = []
  showForm.value = true
}

const cancelForm = () => {
  showForm.value = false
  editingId.value = null
  resetForm()
}

const fetchModels = async () => {
  if (!form.apiKey || !form.baseUrl) { ElMessage.warning('请先填写 API Key 和 Base URL'); return }
  fetchingModels.value = true
  models.value = []
  try {
    const res = await userApi.getModels({ apiKey: form.apiKey, baseUrl: form.baseUrl })
    if (res.code === 1 && res.data?.length) {
      models.value = res.data
      ElMessage.success(`获取到 ${res.data.length} 个模型`)
    } else {
      ElMessage.warning('未获取到模型列表')
    }
  } catch (e: any) { ElMessage.error(e?.msg || '请求失败') }
  finally { fetchingModels.value = false }
}

const handleSave = async () => {
  if (!form.configName.trim() || !form.apiKey.trim() || !form.modelName.trim() || !form.baseUrl.trim()) {
    ElMessage.warning('请填写完整信息'); return
  }
  saving.value = true
  try {
    const payload = { ...form, userId: userId() }
    if (editingId.value) {
      const res = await userApi.updateConfig({ id: editingId.value, ...payload })
      if (res.code === 1) { ElMessage.success('更新成功'); cancelForm(); await loadConfigs() }
      else ElMessage.error(res.msg || '更新失败')
    } else {
      const res = await userApi.saveConfig(payload)
      if (res.code === 1) { ElMessage.success('保存成功'); cancelForm(); await loadConfigs() }
      else ElMessage.error(res.msg || '保存失败')
    }
  } catch (e: any) { ElMessage.error(e?.msg || '保存失败') }
  finally { saving.value = false }
}

const handleTest = async () => {
  if (!form.apiKey || !form.baseUrl) { ElMessage.warning('请先填写 API Key 和 Base URL'); return }
  testing.value = true
  try {
    const res = await userApi.getModels({ apiKey: form.apiKey, baseUrl: form.baseUrl })
    if (res.code === 1) ElMessage.success('测试通过！')
    else ElMessage.error(res.msg || '测试失败')
  } catch (e: any) { ElMessage.error(e?.msg || '测试失败') }
  finally { testing.value = false }
}

const handleDelete = async (config: UserApi) => {
  try {
    await ElMessageBox.confirm(`确定删除"${config.configName}"？`, '确认删除', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    deletingId.value = config.id
    const res = await userApi.deleteConfig({ id: config.id, userId: userId() })
    if (res.code === 1) { ElMessage.success('已删除'); if (editingId.value === config.id) cancelForm(); await loadConfigs() }
    else ElMessage.error(res.msg || '删除失败')
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.msg || '删除失败') }
  finally { deletingId.value = null }
}

const onOpen = () => { loadConfigs() }

const open = () => {
  showForm.value = false
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

defineExpose({ open })
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-8);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
.url-cell { color: var(--color-text-secondary); font-size: var(--font-size-xs); word-break: break-all; }
.key-cell { font-family: var(--font-mono); font-size: var(--font-size-xs); color: var(--color-text-tertiary); background: var(--color-bg-page); padding: 2px 6px; border-radius: var(--radius-sm); }
.edit-form { margin-top: var(--space-2); }
.edit-form-title { font-size: var(--font-size-base); font-weight: var(--font-weight-semibold); margin-bottom: var(--space-3); color: var(--color-text-primary); }
.model-row { display: flex; gap: var(--space-2); width: 100%; }
.model-input { flex: 1; }
.model-list { max-height: 180px; overflow-y: auto; border: 1px solid var(--color-border-light); border-radius: var(--radius-md); margin-top: var(--space-2); }
.model-item { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3); cursor: pointer; border-bottom: 1px solid var(--color-border-light); font-size: var(--font-size-sm); transition: background var(--transition-fast); }
.model-item:last-child { border-bottom: none; }
.model-item:hover { background: var(--color-bg-page); }
.model-item.selected { background: var(--color-primary-light); }
.model-id { font-weight: var(--font-weight-medium); flex: 1; }
.model-owned { font-size: var(--font-size-xs); color: var(--color-text-tertiary); }
.radio-empty { width: 16px; height: 16px; border: 2px solid var(--color-border); border-radius: 50%; flex-shrink: 0; }
.edit-actions { display: flex; gap: var(--space-2); justify-content: flex-end; margin-top: var(--space-3); }
.slide-fade-enter-active { transition: all 0.3s ease; }
.slide-fade-leave-active { transition: all 0.2s ease-in; }
.slide-fade-enter-from, .slide-fade-leave-to { opacity: 0; transform: translateY(-8px); }
</style>
