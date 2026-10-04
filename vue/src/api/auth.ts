import axios from 'axios'
import type { Result, TokenResponse, LoginDTO, RegisterDTO, RefreshTokenDTO } from '../types'

const BASE_URL = 'http://localhost:8080'

const api = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  }
})

api.interceptors.request.use(
  (config) => {
    if (config.skipAuth) {
      return config
    }
    
    const token = localStorage.getItem('accessToken')
    if (token) {
      config.headers['beiming'] = token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

let isRefreshing = false
let refreshSubscribers: Array<(token: string) => void> = []

const subscribeTokenRefresh = (callback: (token: string) => void) => {
  refreshSubscribers.push(callback)
}

const onTokenRefreshed = (token: string) => {
  refreshSubscribers.forEach(callback => callback(token))
  refreshSubscribers = []
}

api.interceptors.response.use(
  (response) => {
    console.log('✅ 请求成功:', response.config.url, response.status)
    return response.data
  },
  async (error) => {
    console.log('❌ 请求失败:', error.config?.url, error.response?.status)
    const originalRequest = error.config

    console.log('Response error:', error.response?.status, error.config?.url)

    if (error.response?.status === 403) {
      console.log('403 error - redirecting to login')
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userId')
      localStorage.removeItem('username')
      window.location.href = '/login'
      return Promise.reject(error.response?.data || error)
    }

    if (error.response?.status === 401 && !originalRequest._retry) {
      console.log('401 error - attempting token refresh')
      
      if (isRefreshing) {
        console.log('Refresh already in progress, queuing request')
        return new Promise((resolve) => {
          subscribeTokenRefresh((token: string) => {
            originalRequest.headers['beiming'] = token
            resolve(api(originalRequest))
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const refreshToken = localStorage.getItem('refreshToken')
        console.log('Refresh token from localStorage:', refreshToken ? 'exists' : 'not found')
        
        if (!refreshToken) {
          throw new Error('No refresh token available')
        }

        const response = await api.post('/user/refresh', { refreshToken }, {
          skipAuth: true
        })
        console.log('Refresh response:', response)
        console.log('Refresh response.data:', response.data)
        console.log('Refresh response.code:', response.code)
        
        let newToken, newRefreshToken
        
        if (response.code === 1 && response.data) {
          newToken = response.data.accessToken
          newRefreshToken = response.data.refreshToken
          console.log('使用 response.data 格式 (Result<TokenResponse>)')
        } else if (response.accessToken) {
          newToken = response.accessToken
          newRefreshToken = response.refreshToken
          console.log('使用直接格式 (TokenResponse)')
        } else {
          console.error('无法从响应中提取令牌，完整响应:', JSON.stringify(response))
          throw new Error('Invalid refresh response format')
        }
        
        console.log('New access token:', newToken ? 'received' : 'not found')

        localStorage.setItem('accessToken', newToken)
        if (newRefreshToken) {
          localStorage.setItem('refreshToken', newRefreshToken)
        }
        onTokenRefreshed(newToken)

        originalRequest.headers['beiming'] = newToken
        const retryResponse = await api(originalRequest)
        return retryResponse
      } catch (refreshError) {
        console.error('Token refresh failed:', refreshError)
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
        localStorage.removeItem('userId')
        localStorage.removeItem('username')
        window.location.href = '/login'
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    return Promise.reject(error.response?.data || error)
  }
)

export const authApi = {
  login: async (data: LoginDTO): Promise<Result<TokenResponse>> => {
    const response = await api.post('/user/login', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
    return response
  },

  register: async (data: RegisterDTO): Promise<Result<null>> => {
    const response = await api.post('/user/register', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
    return response
  },

  refreshToken: async (data: RefreshTokenDTO): Promise<Result<TokenResponse>> => {
    return await api.post('/user/refresh', data, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
  }
}

export default api