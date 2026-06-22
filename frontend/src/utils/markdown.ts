/**
 * 简易 Markdown 渲染（支持 **bold**, `code`, ```code block```, 换行，表格，列表）
 * HomeView 和 MaterialChatSidebar 共享。
 */
export function renderMarkdown(text: string): string {
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // 代码块
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre class="md-code-block"><code>$2</code></pre>')
  // 行内代码
  html = html.replace(/`([^`]+)`/g, '<code class="md-inline-code">$1</code>')
  // 粗体
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  // 表格分隔行
  html = html.replace(/\|(.+)\|/g, (match: string) => {
    if (match.includes('---')) return ''
    const cells = match.split('|').filter(Boolean).map((c: string) => c.trim())
    const row = cells.map((c: string) => `<td>${c}</td>`).join('')
    return `<tr>${row}</tr>`
  })
  // 包装连续 <tr> 为 <table>
  html = html.replace(/(<tr>[\s\S]*?<\/tr>)+/g, '<table class="md-table">$&</table>')
  // 无序列表
  html = html.replace(/^- (.+)$/gm, '<li>$1</li>')
  html = html.replace(/(<li>[\s\S]*?<\/li>)+/g, '<ul class="md-list">$&</ul>')
  // 有序列表
  html = html.replace(/^\d+\. (.+)$/gm, '<li>$1</li>')
  // 换行
  html = html.replace(/\n\n/g, '</p><p>')
  html = html.replace(/\n/g, '<br>')
  html = `<p>${html}</p>`

  return html
}
