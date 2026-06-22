/**
 * Markdown 渲染（基于 markdown-it，GFM 表格/标题/列表/代码均支持）
 * + DOMPurify 净化，防止 AI 输出中的 HTML/脚本注入。
 * HomeView 和 MaterialChatSidebar 共享。
 */
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const md = new MarkdownIt({
  html: false, // 不解析原始 HTML 标签，杜绝注入
  linkify: true, // 自动识别裸链接
  breaks: false // 单换行不转 <br>，遵循标准 Markdown，避免 AI 输出空行叠加成大间距
})

export function renderMarkdown(text: string): string {
  if (!text) return ''
  return DOMPurify.sanitize(md.render(text))
}
