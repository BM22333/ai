import api from './auth'
import type { Result, InsertPrompt, CreateInsertPromptDTO, UpdateInsertPromptDTO } from '../types'

export const insertPromptApi = {
  /** 获取用户的注入提示词列表 */
  list: async (userId: string): Promise<Result<InsertPrompt[]>> => {
    return await api.post('/ai/insertPrompt/list', { userId }, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  },

  /** 创建注入提示词 */
  create: async (data: CreateInsertPromptDTO): Promise<Result<InsertPrompt>> => {
    return await api.post('/ai/insertPrompt/create', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  },

  /** 更新注入提示词（仅更新非 null 字段） */
  update: async (data: UpdateInsertPromptDTO): Promise<Result<null>> => {
    return await api.post('/ai/insertPrompt/update', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  },

  /** 删除注入提示词 */
  delete: async (id: number, userId: string): Promise<Result<null>> => {
    return await api.post('/ai/insertPrompt/delete', { id, userId }, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  }
}
