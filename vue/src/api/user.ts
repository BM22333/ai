import api from './auth'
import type { Result, UserApiDTO, ModelInfo, UserIdDTO, UserApi, DeleteUserApiDTO } from '../types'

export const userApi = {
  /**
   * 获取可用模型列表（同时验证 API Key 和 Base URL 有效性）
   */
  getModels: async (data: UserApiDTO): Promise<Result<ModelInfo[]>> => {
    return await api.post('/user/models', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  },

  /**
   * 保存 API 配置（新增）
   */
  saveConfig: async (data: UserApiDTO): Promise<Result<null>> => {
    return await api.post('/user/config', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  },

  /**
   * 更新 API 配置（编辑）
   */
  updateConfig: async (data: UserApi): Promise<Result<null>> => {
    return await api.post('/user/config/update', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  },

  /**
   * 查询所有已保存的 API 配置（返回数组）
   */
  getApiConfigs: async (data: UserIdDTO): Promise<Result<UserApi[]>> => {
    const response = await api.post('/user/api-config', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
    // 兼容后端尚未改为返回数组的情况：单对象自动包装为数组
    if (response && response.data && !Array.isArray(response.data)) {
      response.data = [response.data]
    }
    // 归一化字段名：后端 Jackson 可能将 baseUrl 序列化为 baseURL
    if (response && response.data && Array.isArray(response.data)) {
      response.data = response.data.map((item: any) => ({
        ...item,
        baseUrl: item.baseUrl ?? item.baseURL ?? '',
      }))
    }
    return response
  },

  /**
   * 删除 API 配置
   */
  deleteConfig: async (data: DeleteUserApiDTO): Promise<Result<null>> => {
    return await api.post('/user/config/delete', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  }
}
