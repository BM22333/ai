export interface Result<T> {
  code: number
  msg: string | null
  data: T | null
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  userId?: string
}

export interface LoginDTO {
  username: string
  password: string
}

export interface RegisterDTO {
  username: string
  password: string
}

export interface RefreshTokenDTO {
  refreshToken: string
}

export interface ToolCall {
  id: string
  type: string
  functionName: string
  arguments: string
  result?: string
}

export interface Message {
  role: string
  content: string
  timestamp: Date
  toolCalls?: ToolCall[] | null
  toolCallId?: string | null
  toolName?: string | null
  /** 消息深度（工具调用轮次），从 0 开始 */
  depth?: number
  /** AI 是否可见。false = 已隐藏；null/undefined（旧数据）视为可见 */
  isSeeable?: boolean | null
}

export interface CreateConversationDTO {
  conversationName: string
  systemPrompt?: string
  apiKey: string
  model?: string
  baseUrl?: string
  userId: string
  /** 展示给 AI 的最大消息数（默认 100，<=0 回退默认） */
  maxMessages?: number
  /** 最大工具调用深度（默认 10，<=0 回退默认） */
  maxToolCallDepth?: number
  /** 历史后置提示词，紧跟历史消息之后发给 AI */
  afterHistoryPrompt?: string
  /** 温度参数 (0~2)，控制回复随机性，默认 0.7 */
  temperature?: number
  /** 推理强度：none / minimal / low / medium / high / xhigh / max */
  reasoningEffort?: string
}

export interface ChatDTO {
  message: string
  userId: string
  conversationId: string
  toolNames?: string[]
  enableTools?: boolean
  /** 本次要注入的提示词 ID 列表 */
  insertPromptIds?: number[]
}

export interface ToolInfo {
  name: string
  description: string
  providerType: string
}

export interface UpdateConversationDTO {
  conversationId: string
  conversationName?: string
  systemPrompt?: string
  model?: string
  baseUrl?: string
  apiKey?: string
  toolPreferences?: string
  /** 展示给 AI 的最大消息数 */
  maxMessages?: number
  /** 最大工具调用深度 */
  maxToolCallDepth?: number
  /** 历史后置提示词 */
  afterHistoryPrompt?: string
  /** 温度参数 (0~2) */
  temperature?: number
  /** 推理强度 */
  reasoningEffort?: string
}

export interface RollbackDTO {
  conversationId: string
  timestamp: number
}

export interface UserApiDTO {
  apiKey: string
  baseUrl: string
  configName?: string
  userId?: string
  modelName?: string
}

export interface ModelInfo {
  id: string
  object: string
  created: number
  ownedBy: string
}

export interface UserIdDTO {
  userId: string
}

export interface UserApi {
  id: number
  userId: string
  configName: string
  apiKey: string
  modelName: string
  baseUrl: string
}

export interface EditMessageDTO {
  conversationId: string
  messageIndex: number
  newContent: string
}

export interface DeleteMessageDTO {
  conversationId: string
  messageIndex: number
}

export interface DeleteUserApiDTO {
  id: number
  userId: string
}

// ===== MCP 相关类型 =====

/** 请求头键值对 */
export interface HeaderEntry {
  key: string
  value: string
}

/** MCP Server 信息（来自 /mcp/servers/list） */
export interface McpServerInfo {
  name: string
  baseUrl: string
  connected: boolean
  /** 自定义 HTTP 请求头（脱敏后），JSON 对象格式 */
  headers?: Record<string, string>
}

/** MCP 工具信息（来自 /mcp/tools 分组数据） */
export interface McpToolInfo {
  fullName: string
  displayName: string
  description: string
  parametersSchema?: Record<string, any>
}

/** MCP Server 分组（含工具列表） */
export interface McpServerGroup {
  serverName: string
  connected: boolean
  tools: McpToolInfo[]
}

/** 添加 MCP Server 的请求参数 */
export interface AddMcpServerDTO {
  userId: string
  name: string
  baseUrl: string
  /** 自定义 HTTP 请求头（JSON 对象），如 {"X-API-Key":"sk-xxx"} */
  headers?: Record<string, string>
}

/** 删除/重连/健康检查 MCP Server 的请求参数 */
export interface McpServerActionDTO {
  userId: string
}

/** 前端展示用的统一工具类型 */
export interface DisplayTool {
  fullName: string
  displayName: string
  description: string
  providerType: 'BUILTIN' | 'MCP'
  serverName?: string
  parametersSchema?: Record<string, any>
}

// ===== 注入提示词相关类型 =====

/** 注入提示词 */
export interface InsertPrompt {
  id: number
  userId?: string
  promptName: string
  content: string
  depth: number
  createTime?: string
}

/** 创建注入提示词 */
export interface CreateInsertPromptDTO {
  userId: string
  promptName: string
  content: string
  depth: number
}

/** 更新注入提示词（仅更新非 null 字段） */
export interface UpdateInsertPromptDTO {
  id: number
  userId: string
  promptName?: string
  content?: string
  depth?: number
}

// ===== 消息隐藏/恢复相关类型 =====

/** 批量隐藏消息 */
export interface HideMessagesDTO {
  conversationId: string
  messageIndexes: number[]
}

/** 批量恢复显示消息 */
export interface ShowMessagesDTO {
  conversationId: string
  messageIndexes: number[]
}