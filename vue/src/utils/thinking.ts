/**
 * 解析 AI 输出中的 <thinking> 标签，将内容拆分为普通文本和思考过程片段。
 *
 * 支持的格式：
 *   <thinking>思考内容</thinking>   — 完整的思考块
 *   <thinking>思考内容...           — 未闭合的思考块（流式输出中）
 *
 * 返回的每个片段包含：
 *   - type: 'text' | 'thinking'
 *   - content: 该片段的内容
 *   - isComplete: 仅 thinking 片段有效，表示标签是否已闭合
 */

export interface ParsedSegment {
  type: 'text' | 'thinking'
  content: string
  /** 仅 thinking 片段有效：标签是否已闭合（</thinking> 是否存在） */
  isComplete?: boolean
}

/**
 * 将包含 <thinking> 标签的文本拆分为片段数组。
 */
export function parseThinking(content: string): ParsedSegment[] {
  if (!content) return []
  const segments: ParsedSegment[] = []

  // 同时匹配完整标签和未闭合标签
  // 组1: 完整 <thinking>...</thinking>
  // 组2: 未闭合 <thinking>...（到字符串末尾）
  const regex = /<thinking>([\s\S]*?)<\/thinking>|<thinking>([\s\S]*)$/g

  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = regex.exec(content)) !== null) {
    // 在 thinking 标签之前的普通文本
    if (match.index > lastIndex) {
      const textContent = content.slice(lastIndex, match.index)
      if (textContent) {
        segments.push({ type: 'text', content: textContent })
      }
    }

    if (match[1] !== undefined) {
      // 完整闭合的 thinking 块
      segments.push({
        type: 'thinking',
        content: match[1],
        isComplete: true
      })
    } else if (match[2] !== undefined) {
      // 未闭合的 thinking 块（流式输出中）
      segments.push({
        type: 'thinking',
        content: match[2],
        isComplete: false
      })
    }

    lastIndex = match.index + match[0].length
  }

  // 最后剩余的普通文本
  if (lastIndex < content.length) {
    const textContent = content.slice(lastIndex)
    if (textContent) {
      segments.push({ type: 'text', content: textContent })
    }
  }

  return segments
}

/**
 * 快速判断一段文本是否包含 <thinking> 标签。
 */
export function hasThinking(content: string): boolean {
  if (!content) return false
  return /<thinking>/i.test(content)
}
