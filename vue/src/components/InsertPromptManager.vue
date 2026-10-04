<template>
  <el-dialog
    v-model="dialogVisible"
    title="注入提示词管理"
    width="700px"
    :close-on-click-modal="false"
    @open="loadPrompts"
  >
    <div class="manager-header">
      <el-button type="primary" size="small" @click="startCreate" v-if="!editingId">
        <el-icon><Plus /></el-icon>
        新建提示词
      </el-button>
    </div>

    <!-- 提示词列表 -->
    <el-table
      v-if="prompts.length > 0 && !editingId"
      :data="prompts"
      style="width: 100%; margin-top: var(--space-3)"
      size="small"
    >
      <el-table-column prop="promptName" label="名称" min-width="140" />
      <el-table-column prop="depth" label="注入深度" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info" effect="plain">depth: {{ row.depth }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="240">
        <template #default="{ row }">
          <span class="content-preview">{{ truncate(row.content, 60) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center">
        <template #default="{ row }">
          <el-button text size="small" type="primary" @click="startEdit(row)">
            <el-icon><Edit /></el-icon>
          </el-button>
          <el-button text size="small" type="danger" @click="handleDelete(row.id)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 空态 -->
    <div v-if="prompts.length === 0 && !editingId && !loading" class="empty-prompts">
      <p>暂无注入提示词，点击"新建提示词"创建第一条</p>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading"><Setting /></el-icon>
      <span>加载中...</span>
    </div>

    <!-- 新建/编辑表单 -->
    <div v-if="editingId !== null" class="edit-form">
      <el-divider />
      <div class="edit-form-title">{{ editMode === 'create' ? '新建提示词' : '编辑提示词' }}</div>
      <el-form :model="editForm" ref="editFormRef" label-width="100px">
        <el-form-item label="名称" prop="promptName" :rules="[{ required: true, message: '请输入名称', trigger: 'blur' }]">
          <el-input v-model="editForm.promptName" placeholder="提示词名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="注入深度" prop="depth">
          <el-input-number v-model="editForm.depth" :min="0" :step="1" style="width: 100%" />
          <div class="field-hint">注入到历史中第一条 depth 相同的消息之前。若该 depth 不存在则追加到历史末尾。</div>
        </el-form-item>
        <el-form-item label="内容" prop="content" :rules="[{ required: true, message: '请输入内容', trigger: 'blur' }]">
          <el-input v-model="editForm.content" type="textarea" :rows="4" placeholder="提示词内容" />
        </el-form-item>
      </el-form>
      <div class="edit-actions">
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
        <el-button @click="cancelEdit">取消</el-button>
      </div>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus, Edit, Delete, Setting } from '@element-plus/icons-vue'
import { insertPromptApi } from '../api/insertPrompt'
import type { InsertPrompt } from '../types'

const dialogVisible = ref(false)
const loading = ref(false)
const saving = ref(false)
const prompts = ref<InsertPrompt[]>([])
const editingId = ref<number | null>(null)
const editMode = ref<'create' | 'edit'>('create')
const editFormRef = ref<FormInstance>()

const editForm = reactive({
  promptName: '',
  content: '',
  depth: 0
})

const userId = () => localStorage.getItem('userId') || 'default_user'

const truncate = (text: string, maxLen: number): string => {
  if (!text) return ''
  return text.length > maxLen ? text.substring(0, maxLen) + '...' : text
}

const loadPrompts = async () => {
  loading.value = true
  try {
    const res = await insertPromptApi.list(userId())
    if (res.code === 1 && res.data) {
      prompts.value = res.data
    }
  } catch (error: any) {
    ElMessage.error(error?.msg || '加载失败')
  } finally {
    loading.value = false
  }
}

const startCreate = () => {
  editMode.value = 'create'
  editForm.promptName = ''
  editForm.content = ''
  editForm.depth = 0
  editingId.value = 0 // non-null marker for create
}

const startEdit = (prompt: InsertPrompt) => {
  editMode.value = 'edit'
  editingId.value = prompt.id
  editForm.promptName = prompt.promptName
  editForm.content = prompt.content
  editForm.depth = prompt.depth
}

const cancelEdit = () => {
  editingId.value = null
  editFormRef.value?.clearValidate()
}

const handleSave = async () => {
  if (!editForm.promptName.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  if (!editForm.content.trim()) {
    ElMessage.warning('请输入内容')
    return
  }

  saving.value = true
  try {
    if (editMode.value === 'create') {
      const res = await insertPromptApi.create({
        userId: userId(),
        promptName: editForm.promptName.trim(),
        content: editForm.content.trim(),
        depth: editForm.depth
      })
      if (res.code === 1) {
        ElMessage.success('创建成功')
        cancelEdit()
        await loadPrompts()
      } else {
        ElMessage.error(res.msg || '创建失败')
      }
    } else {
      const res = await insertPromptApi.update({
        id: editingId.value!,
        userId: userId(),
        promptName: editForm.promptName.trim(),
        content: editForm.content.trim(),
        depth: editForm.depth
      })
      if (res.code === 1) {
        ElMessage.success('更新成功')
        cancelEdit()
        await loadPrompts()
      } else {
        ElMessage.error(res.msg || '更新失败')
      }
    }
  } catch (error: any) {
    ElMessage.error(error?.msg || '操作失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这条注入提示词吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await insertPromptApi.delete(id, userId())
    if (res.code === 1) {
      ElMessage.success('已删除')
      await loadPrompts()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error?.msg || '删除失败')
    }
  }
}

const open = () => {
  editingId.value = null
  dialogVisible.value = true
}

defineExpose({ open })
</script>

<style scoped>
.manager-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.content-preview {
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
  max-width: 100%;
}

.empty-prompts {
  text-align: center;
  padding: var(--space-8);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-8);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.edit-form {
  margin-top: var(--space-2);
}

.edit-form-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--space-3);
}

.edit-actions {
  display: flex;
  gap: var(--space-2);
  justify-content: flex-end;
  margin-top: var(--space-2);
}

.field-hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: var(--space-1);
  line-height: var(--line-height-relaxed);
}
</style>
