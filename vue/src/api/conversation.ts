import api from './auth'
import type { Result, CreateConversationDTO, Message, RollbackDTO, ToolInfo, UpdateConversationDTO, ChatDTO, EditMessageDTO, DeleteMessageDTO, HideMessagesDTO, ShowMessagesDTO } from '../types'

export const conversationApi = {
  createConversation: async (data: CreateConversationDTO): Promise<Result<string>> => {
    return await api.post('/ai/conversation/create', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  },

  getAllConversations: async (userId: string): Promise<Result<any[]>> => {
    console.log('调用 getAllConversations API, userId:', userId)
    const response = await api.post('/ai/conversation/getAll', { userId: userId }, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
    console.log('getAllConversations 响应:', response)
    return response
  },

  connectConversation: async (conversationId: string): Promise<Result<null>> => {
    console.log('调用 connectConversation API, conversationId:', conversationId)
    const response = await api.post('/ai/conversation/connect', { conversationId }, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
    console.log('connectConversation 响应:', response)
    return response
  },

  getHistory: async (conversationId: string): Promise<Result<Message[]>> => {
    return await api.post('/ai/conversation/getHistory', { conversationId }, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  },

  deleteConversation: async (conversationId: string): Promise<Result<null>> => {
    return await api.post('/ai/conversation/delete', { conversationId }, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  },

  rollbackConversation: async (data: RollbackDTO): Promise<Result<null>> => {
    console.log('调用 rollbackConversation API, data:', data)
    const response = await api.post('/ai/conversation/rollback', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
    console.log('rollbackConversation 响应:', response)
    return response
  },

  getAvailableTools: async (): Promise<Result<ToolInfo[]>> => {
    return await api.post('/ai/tools')
  },

  updateConversation: async (data: UpdateConversationDTO): Promise<Result<null>> => {
    return await api.post('/ai/conversation/update', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  },

  editMessage: async (data: EditMessageDTO): Promise<Result<string>> => {
    return await api.post('/ai/conversation/editMessage', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  },

  deleteMessage: async (data: DeleteMessageDTO): Promise<Result<string>> => {
    return await api.post('/ai/conversation/deleteMessage', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  },

  hideMessages: async (data: HideMessagesDTO): Promise<Result<string>> => {
    return await api.post('/ai/conversation/hideMessages', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  },

  showMessages: async (data: ShowMessagesDTO): Promise<Result<string>> => {
    return await api.post('/ai/conversation/showMessages', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  }
}

export const chatApi = {
  sendMessage: async (data: ChatDTO, signal?: AbortSignal, deepThinking?: boolean) => {
    console.log('chatApi.sendMessage 被调用，参数:', data, 'deepThinking:', deepThinking)

    const requestBody: Record<string, any> = {
      message: data.message,
      userId: data.userId,
      conversationId: data.conversationId
    }

    if (data.toolNames !== undefined && data.toolNames !== null) {
      requestBody.toolNames = data.toolNames
    }
    if (data.enableTools !== undefined && data.enableTools !== null) {
      requestBody.enableTools = data.enableTools
    }
    if (data.insertPromptIds !== undefined && data.insertPromptIds !== null && data.insertPromptIds.length > 0) {
      requestBody.insertPromptIds = data.insertPromptIds
    }

    const endpoint = deepThinking ? '/ai/chat/deep-thinking' : '/ai/chat'

    const makeRequest = async (token: string) => {
      const response = await fetch(`http://localhost:8080${endpoint}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          'beiming': token || ''
        },
        body: JSON.stringify(requestBody),
        signal
      })
      
      console.log('fetch响应状态:', response.status)
      console.log('fetch响应headers:', Object.fromEntries(response.headers.entries()))
      
      if (response.status === 401) {
        console.log('收到401，尝试刷新令牌')
        const refreshToken = localStorage.getItem('refreshToken')
        if (!refreshToken) {
          throw new Error('No refresh token available')
        }
        
        const refreshResponse = await fetch('http://localhost:8080/user/refresh', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ refreshToken })
        })
        
        if (!refreshResponse.ok) {
          console.error('刷新令牌失败:', refreshResponse.status)
          localStorage.removeItem('accessToken')
          localStorage.removeItem('refreshToken')
          localStorage.removeItem('userId')
          localStorage.removeItem('username')
          window.location.href = '/login'
          throw new Error('Token refresh failed')
        }
        
        const refreshData = await refreshResponse.json()
        if (refreshData.code === 1 && refreshData.data) {
          const newToken = refreshData.data.accessToken
          const newRefreshToken = refreshData.data.refreshToken
          
          localStorage.setItem('accessToken', newToken)
          if (newRefreshToken) {
            localStorage.setItem('refreshToken', newRefreshToken)
          }
          
          console.log('令牌刷新成功，重试请求')
          return makeRequest(newToken)
        } else {
          throw new Error('Invalid refresh response')
        }
      }
      
      if (response.status === 403) {
        console.log('收到403，清除用户信息并跳转登录')
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
        localStorage.removeItem('userId')
        localStorage.removeItem('username')
        window.location.href = '/login'
        throw new Error('Access forbidden')
      }
      
      if (!response.ok) {
        const errorText = await response.text()
        console.error('fetch响应错误:', errorText)
        throw new Error('发送消息失败: ' + errorText)
      }
      
      console.log('fetch响应成功，返回body')
      return response.body
    }
    
    const token = localStorage.getItem('accessToken')
    console.log('当前token:', token ? '存在' : '不存在')
    
    return await makeRequest(token || '')
  }
}