<template>
  <div class="api-config-page">
    <div class="page-header">
      <el-button text @click="goBack" class="back-btn">
        <el-icon><ArrowLeft /></el-icon>
        返回聊天
      </el-button>
      <h1 class="page-title">API 配置管理</h1>
      <p class="page-desc">配置和管理您的 AI API 密钥，用于创建会话</p>
    </div>

    <!-- 配置列表 -->
    <div class="config-card list-card">
      <div class="card-header">
        <el-icon :size="20"><Setting /></el-icon>
        <span>我的 API 配置</span>
        <el-button type="primary" size="default" @click="handleAddNew" class="add-btn">
          <el-icon><Plus /></el-icon>
          添加配置
        </el-button>
      </div>

      <div class="card-body" v-loading="loadingConfigs">
        <!-- 空状态 -->
        <div v-if="!loadingConfigs && configs.length === 0" class="empty-state">
          <el-icon :size="56" color="#c0c4cc"><FolderOpened /></el-icon>
          <p>暂无 API 配置</p>
          <p class="sub-text">点击"添加配置"来配置您的第一个 API</p>
        </div>

        <!-- 配置列表 -->
        <el-table
          v-else
          :data="configs"
          stripe
          highlight-current-row
          style="width: 100%"
          row-class-name="config-row"
        >
          <el-table-column prop="configName" label="配置名称" min-width="140">
            <template #default="{ row }">
              <span class="config-name-cell">{{ row.configName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="modelName" label="模型" min-width="200">
            <template #default="{ row }">
              <el-tag type="primary" effect="light" size="small">{{ row.modelName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="baseUrl" label="Base URL" min-width="200">
            <template #default="{ row }">
              <span class="url-cell">{{ row.baseUrl }}</span>
            </template>
          </el-table-column>
          <el-table-column label="API Key" width="150">
            <template #default="{ row }">
              <code class="key-cell">{{ maskApiKey(row.apiKey) }}</code>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="handleEdit(row)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button
                text
                type="danger"
                size="small"
                :loading="deletingId === row.id"
                @click="handleDelete(row)"
              >
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 添加/编辑表单 -->
    <transition name="slide-fade">
      <div v-if="showForm" class="config-card form-card">
        <div class="card-header">
          <el-icon :size="20"><component :is="editingId ? 'Edit' : 'Plus'" /></el-icon>
          <span>{{ editingId ? '编辑配置' : '添加新配置' }}</span>
          <el-button text @click="cancelForm" class="close-btn">
            <el-icon :size="18"><Close /></el-icon>
          </el-button>
        </div>

        <div class="card-body">
          <el-form :model="testForm" :rules="testRules" ref="testFormRef" label-position="top">
            <!-- 配置名称 -->
            <el-form-item label="配置名称" prop="configName">
              <el-input
                v-model="testForm.configName"
                placeholder="为这个配置起个名字，例如：我的DeepSeek"
                size="large"
                maxlength="30"
                show-word-limit
              />
            </el-form-item>

            <!-- API Key -->
            <el-form-item label="API 密钥" prop="apiKey">
              <el-input
                v-model="testForm.apiKey"
                type="password"
                placeholder="sk-xxxxxxxx"
                show-password
                size="large"
              />
            </el-form-item>

            <!-- 模型名称：手动输入 + 从列表选择 -->
            <el-form-item label="模型名称" prop="modelName">
              <div class="baseurl-row">
                <el-input
                  v-model="testForm.modelName"
                  placeholder="输入模型名称，或点击右侧按钮从列表中选取"
                  size="large"
                  class="baseurl-input"
                  clearable
                />
                <el-button
                  type="primary"
                  plain
                  size="large"
                  :loading="fetchingModels"
                  @click="handleFetchModels"
                  class="fetch-btn"
                >
                  <el-icon><Download /></el-icon>
                  获取模型列表
                </el-button>
              </div>
            </el-form-item>

            <!-- Base URL -->
            <el-form-item label="API 基础地址" prop="baseUrl">
              <el-input
                v-model="testForm.baseUrl"
                placeholder="https://api.siliconflow.cn"
                size="large"
              >
                <template #prefix>
                  <el-icon><Link /></el-icon>
                </template>
              </el-input>
            </el-form-item>
          </el-form>

          <!-- 模型列表（获取后显示） -->
          <div v-if="models.length > 0" class="models-section">
            <div class="section-title">
              <el-icon><Grid /></el-icon>
              <span>可用模型列表（点击选择）</span>
              <el-tag size="small" type="info">{{ models.length }} 个</el-tag>
            </div>

            <div class="model-list">
              <div
                v-for="model in models"
                :key="model.id"
                class="model-item"
                :class="{ selected: testForm.modelName === model.id }"
                @click="selectModelFromList(model.id)"
              >
                <div class="model-main">
                  <div class="model-check">
                    <el-icon v-if="selectedModel === model.id" :size="18" color="#1890ff">
                      <SuccessFilled />
                    </el-icon>
                    <span v-else class="radio-empty"></span>
                  </div>
                  <div class="model-info">
                    <div class="model-name">{{ model.id }}</div>
                    <div class="model-meta">厂商：{{ model.ownedBy }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 空模型提示 -->
          <div v-if="showEmptyModels" class="empty-models">
            <el-empty description="未获取到可用模型，请检查 API Key 和 Base URL 是否正确" />
          </div>

          <!-- 底部操作栏：左侧确认配置，右侧测试 -->
          <div class="form-actions">
            <el-button
              type="success"
              size="large"
              :loading="savingConfig"
              :disabled="!testForm.modelName.trim()"
              @click="handleSaveConfig"
              class="save-btn"
            >
              <el-icon><Select /></el-icon>
              {{ editingId ? '更新配置' : '确认配置' }}
            </el-button>
            <el-button
              type="primary"
              size="large"
              :loading="testingConfig"
              @click="handleTestConfig"
              class="test-config-btn"
            >
              <el-icon><Connection /></el-icon>
              测试
            </el-button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  ArrowLeft,
  Connection,
  Setting,
  Plus,
  Edit,
  Delete,
  Grid,
  Select,
  SuccessFilled,
  FolderOpened,
  Close,
  Link,
  Download
} from '@element-plus/icons-vue'
import { userApi } from '../api/user'
import type { ModelInfo, UserApi } from '../types'

const router = useRouter()
const testFormRef = ref<FormInstance>()

const testForm = reactive({
  configName: '',
  apiKey: '',
  modelName: '',
  baseUrl: 'https://api.siliconflow.cn'
})

const testRules: FormRules = {
  configName: [
    { required: true, message: '请输入配置名称', trigger: 'blur' }
  ],
  apiKey: [
    { required: true, message: '请输入 API 密钥', trigger: 'blur' },
    { min: 5, message: 'API Key 长度至少 5 个字符', trigger: 'blur' }
  ],
  modelName: [
    { required: true, message: '请输入或选择模型名称', trigger: 'blur' }
  ],
  baseUrl: [
    { required: true, message: '请输入 API 基础地址', trigger: 'blur' }
  ]
}

const configs = ref<UserApi[]>([])
const loadingConfigs = ref(false)
const fetchingModels = ref(false)
const testingConfig = ref(false)
const savingConfig = ref(false)
const deletingId = ref<number | null>(null)
const models = ref<ModelInfo[]>([])
const showForm = ref(false)
const editingId = ref<number | null>(null)
const showEmptyModels = ref(false)

const maskApiKey = (key: string) => {
  if (!key) return '****'
  if (key.length <= 8) return '****'
  return key.substring(0, 4) + '****' + key.substring(key.length - 4)
}

const goBack = () => {
  router.push('/chat')
}

// ---- 配置列表操作 ----

const loadAllConfigs = async () => {
  loadingConfigs.value = true
  try {
    const userId = localStorage.getItem('userId') || 'default_user'
    const response = await userApi.getApiConfigs({ userId })

    if (response.code === 1 && response.data) {
      configs.value = response.data
    } else {
      configs.value = []
    }
  } catch {
    configs.value = []
  } finally {
    loadingConfigs.value = false
  }
}

const resetForm = () => {
  testForm.configName = ''
  testForm.apiKey = ''
  testForm.modelName = ''
  testForm.baseUrl = 'https://api.siliconflow.cn'
  models.value = []
  showEmptyModels.value = false
  testFormRef.value?.clearValidate()
}

const handleAddNew = () => {
  editingId.value = null
  resetForm()
  showForm.value = true
}

const handleEdit = (config: UserApi) => {
  editingId.value = config.id
  testForm.configName = config.configName
  testForm.apiKey = config.apiKey
  testForm.modelName = config.modelName
  testForm.baseUrl = config.baseUrl
  models.value = []
  showEmptyModels.value = false
  showForm.value = true
  testFormRef.value?.clearValidate()
}

const cancelForm = () => {
  showForm.value = false
  editingId.value = null
  resetForm()
}

const handleDelete = async (config: UserApi) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除配置"${config.configName}"吗？此操作不可恢复，但不会影响已创建的会话。`,
      '确认删除',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    deletingId.value = config.id
    const userId = localStorage.getItem('userId') || 'default_user'
    const response = await userApi.deleteConfig({ id: config.id, userId })

    if (response.code === 1) {
      ElMessage.success('删除成功')
      if (editingId.value === config.id) {
        cancelForm()
      }
      await loadAllConfigs()
    } else {
      ElMessage.error(response.msg || '删除失败')
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error?.msg || '删除失败')
    }
  } finally {
    deletingId.value = null
  }
}

// ---- 获取模型列表 & 测试 & 保存 ----

const handleFetchModels = async () => {
  if (!testFormRef.value) return

  await testFormRef.value.validate(async (valid) => {
    if (!valid) return

    fetchingModels.value = true
    models.value = []
    showEmptyModels.value = false

    try {
      const response = await userApi.getModels({
        apiKey: testForm.apiKey,
        baseUrl: testForm.baseUrl
      })

      if (response.code === 1 && response.data && response.data.length > 0) {
        models.value = response.data
        ElMessage.success(`成功获取 ${response.data.length} 个可用模型，点击即可选择`)
      } else if (response.code === 1 && (!response.data || response.data.length === 0)) {
        showEmptyModels.value = true
        ElMessage.warning('API 验证通过，但未获取到模型列表')
      } else {
        ElMessage.error(response.msg || '获取模型列表失败')
      }
    } catch (error: any) {
      ElMessage.error(error?.msg || '请求失败，请检查 API Key 和 Base URL')
    } finally {
      fetchingModels.value = false
    }
  })
}

const selectModelFromList = (modelId: string) => {
  testForm.modelName = modelId
}

const handleTestConfig = async () => {
  if (!testFormRef.value) return

  await testFormRef.value.validate(async (valid) => {
    if (!valid) return

    testingConfig.value = true

    try {
      const response = await userApi.getModels({
        apiKey: testForm.apiKey,
        baseUrl: testForm.baseUrl
      })

      if (response.code === 1) {
        ElMessage.success('测试通过！API Key 和 Base URL 验证成功')
      } else {
        ElMessage.error(response.msg || '测试失败，请检查配置')
      }
    } catch (error: any) {
      ElMessage.error(error?.msg || '测试请求失败，请检查 API Key 和 Base URL')
    } finally {
      testingConfig.value = false
    }
  })
}

const handleSaveConfig = async () => {
  const modelName = testForm.modelName.trim()
  if (!modelName) {
    ElMessage.warning('请输入或选择一个模型')
    return
  }

  savingConfig.value = true

  try {
    const userId = localStorage.getItem('userId') || 'default_user'

    if (editingId.value) {
      // 编辑模式：调用 updateConfig
      const response = await userApi.updateConfig({
        id: editingId.value,
        userId,
        configName: testForm.configName,
        apiKey: testForm.apiKey,
        baseUrl: testForm.baseUrl,
        modelName
      })

      if (response.code === 1) {
        ElMessage.success('配置更新成功！')
        cancelForm()
        await loadAllConfigs()
      } else {
        ElMessage.error(response.msg || '更新失败')
      }
    } else {
      // 新增模式：调用 saveConfig
      const response = await userApi.saveConfig({
        apiKey: testForm.apiKey,
        baseUrl: testForm.baseUrl,
        modelName,
        configName: testForm.configName,
        userId
      })

      if (response.code === 1) {
        ElMessage.success('配置保存成功！')
        cancelForm()
        await loadAllConfigs()
      } else {
        ElMessage.error(response.msg || '保存失败')
      }
    }
  } catch (error: any) {
    ElMessage.error(error?.msg || '保存失败')
  } finally {
    savingConfig.value = false
  }
}

onMounted(() => {
  loadAllConfigs()
})
</script>

<style scoped>
.api-config-page {
  min-height: 100vh;
  background: var(--color-bg-page);
  padding: var(--space-8) var(--space-10);
}

.page-header {
  margin-bottom: var(--space-6);
}

.back-btn {
  margin-bottom: var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  transition: color var(--transition-fast);
}

.back-btn:hover {
  color: var(--color-primary);
}

.page-title {
  margin: 0 0 var(--space-2) 0;
  font-family: var(--font-heading);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  letter-spacing: -0.01em;
}

.page-desc {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

/* Card */
.config-card {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  overflow: hidden;
  border: 1px solid var(--color-border-light);
  margin-bottom: var(--space-5);
  transition: box-shadow var(--transition-base);
}

.config-card:hover {
  box-shadow: var(--shadow-lg);
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-6);
  border-bottom: 1px solid var(--color-border-light);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  background: var(--color-bg-page);
}

.add-btn {
  margin-left: auto;
}

.close-btn {
  margin-left: auto;
  color: var(--color-text-tertiary);
}

.close-btn:hover {
  color: var(--color-text-primary);
}

.card-body {
  padding: var(--space-6);
}

/* Empty state */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-12) var(--space-4);
  color: var(--color-text-tertiary);
  text-align: center;
}

.empty-state p {
  margin: var(--space-3) 0 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.empty-state .sub-text {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: var(--space-1);
}

/* Table cells */
.config-name-cell {
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.url-cell {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  word-break: break-all;
}

.key-cell {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  background: var(--color-bg-page);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}

:deep(.config-row) {
  cursor: default;
}

:deep(.el-table__body tr:hover > td) {
  background-color: var(--color-bg-surface-hover) !important;
}

/* Form */
.baseurl-row {
  display: flex;
  gap: var(--space-3);
  align-items: center;
}

.baseurl-input {
  flex: 1;
}

.fetch-btn {
  flex-shrink: 0;
  white-space: nowrap;
}

/* Bottom actions */
.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--space-6);
  padding-top: var(--space-5);
  border-top: 1px solid var(--color-border-light);
}

.save-btn {
  min-width: 160px;
  height: 48px;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}

.test-config-btn {
  min-width: 120px;
  height: 48px;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
}

/* Models list */
.models-section {
  margin-top: var(--space-6);
  border-top: 1px solid var(--color-border-light);
  padding-top: var(--space-5);
}

.section-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.section-title .el-tag {
  margin-left: auto;
}

.model-list {
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
}

.model-item {
  padding: var(--space-3) var(--space-4);
  cursor: pointer;
  transition: all var(--transition-fast);
  border-bottom: 1px solid var(--color-border-light);
}

.model-item:last-child {
  border-bottom: none;
}

.model-item:hover {
  background: var(--color-bg-surface-hover);
}

.model-item.selected {
  background: var(--color-primary-light);
  border-left: 3px solid var(--color-primary);
}

.model-main {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.model-check {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.radio-empty {
  width: 18px;
  height: 18px;
  border: 2px solid var(--color-border);
  border-radius: 50%;
}

.model-info {
  flex: 1;
  min-width: 0;
}

.model-name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  word-break: break-all;
}

.model-meta {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: 2px;
}

.empty-models {
  margin-top: var(--space-6);
  border-top: 1px solid var(--color-border-light);
  padding-top: var(--space-5);
}

/* Slide transition */
.slide-fade-enter-active {
  transition: all 300ms cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-fade-leave-active {
  transition: all 200ms ease-in;
}

.slide-fade-enter-from {
  opacity: 0;
  transform: translateY(-16px);
}

.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* Responsive */
@media (max-width: 900px) {
  .api-config-page {
    padding: var(--space-5) var(--space-4);
  }

  .card-body {
    padding: var(--space-4);
  }
}
</style>
