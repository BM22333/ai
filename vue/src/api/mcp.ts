import api from './auth'
import type {
  Result,
  ToolInfo,
  McpServerInfo,
  McpServerGroup,
  AddMcpServerDTO,
  McpServerActionDTO
} from '../types'

export const mcpApi = {
  /**
   * 添加 MCP Server，返回该 Server 下的工具列表。
   */
  addServer: async (data: AddMcpServerDTO): Promise<Result<ToolInfo[]>> => {
    return await api.post('/mcp/servers', data, {
      headers: { 'Content-Type': 'application/json' }
    })
  },

  /**
   * 删除指定 MCP Server。
   */
  deleteServer: async (serverName: string, data: McpServerActionDTO): Promise<Result<null>> => {
    return await api.delete(`/mcp/servers/${serverName}`, {
      data,
      headers: { 'Content-Type': 'application/json' }
    })
  },

  /**
   * 列出所有 MCP Server（含连接状态）。
   */
  listServers: async (data: McpServerActionDTO): Promise<Result<McpServerInfo[]>> => {
    return await api.post('/mcp/servers/list', data, {
      headers: { 'Content-Type': 'application/json' }
    })
  },

  /**
   * 重连指定 MCP Server，返回该 Server 下的工具列表。
   */
  reconnectServer: async (serverName: string, data: McpServerActionDTO): Promise<Result<ToolInfo[]>> => {
    return await api.post(`/mcp/servers/${serverName}/reconnect`, data, {
      headers: { 'Content-Type': 'application/json' }
    })
  },

  /**
   * 健康检查指定 MCP Server。
   */
  checkHealth: async (serverName: string, data: McpServerActionDTO): Promise<Result<{ connected: boolean }>> => {
    return await api.post(`/mcp/servers/${serverName}/health`, data, {
      headers: { 'Content-Type': 'application/json' }
    })
  },

  /**
   * 获取 MCP 工具列表（按 Server 分组，含 displayName）。
   * 这是前端工具选择界面的首选数据源。
   */
  getMcpTools: async (data: McpServerActionDTO): Promise<Result<McpServerGroup[]>> => {
    return await api.post('/mcp/tools', data, {
      headers: { 'Content-Type': 'application/json' }
    })
  },

  /**
   * 删除所有 MCP Server（断开连接 + 注销工具 + 删除记录）。
   */
  deleteAllServers: async (data: McpServerActionDTO): Promise<Result<null>> => {
    return await api.delete('/mcp/servers', {
      data,
      headers: { 'Content-Type': 'application/json' }
    })
  },

  /**
   * 注销用户（禁用账号 + 清除令牌 + 删除 API 配置 + 清理 MCP Server）。
   */
  deactivateUser: async (data: McpServerActionDTO): Promise<Result<null>> => {
    return await api.post('/user/deactivate', data, {
      headers: { 'Content-Type': 'application/json' }
    })
  }
}

// ===== 工具函数 =====

/**
 * 根据已知的 MCP Server 列表，解析完整工具名。
 * 将 `mcp_uuid_weather_get_forecast` 解析为 `{ serverName: 'weather', toolName: 'get_forecast' }`。
 */
export function parseMcpToolFullName(
  fullName: string,
  knownServers: { name: string }[]
): { serverName: string; toolName: string } | null {
  if (!fullName.startsWith('mcp_')) return null

  // 去掉 "mcp_" 前缀
  const inner = fullName.slice(4)

  // 找到第一个下划线的位置（userId 之后）
  const firstUnderscore = inner.indexOf('_')
  if (firstUnderscore === -1) return null

  // userId 之后的部分 = serverName + '_' + toolName
  const afterUserId = inner.slice(firstUnderscore + 1)

  // 用已知的 serverName 列表精确匹配
  for (const server of knownServers) {
    const prefix = server.name + '_'
    if (afterUserId.startsWith(prefix)) {
      return {
        serverName: server.name,
        toolName: afterUserId.slice(prefix.length)
      }
    }
  }

  return null
}

/**
 * 获取 MCP 工具的展示名称。
 * 如 "天气查询 / get_forecast"。
 */
export function getDisplayToolName(
  fullName: string,
  knownServers: { name: string }[]
): string {
  const parsed = parseMcpToolFullName(fullName, knownServers)
  if (parsed) {
    return `${parsed.serverName} / ${parsed.toolName}`
  }
  return fullName
}

/**
 * 判断工具名是否为 MCP 工具。
 */
export function isMcpTool(name: string): boolean {
  return name.startsWith('mcp_')
}
