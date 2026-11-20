/**
 * 极简 Markdown 渲染器，把 AI 返回的文本转成 HTML 片段，供 v-html 使用。
 *
 * 为什么不引入 markdown-it / marked：AI 回答里只会出现加粗、列表、代码、标题这类
 * 常用语法，一个百来行的自实现足够，还省去额外依赖与 XSS 消毒的成本。
 * 安全策略：先把全部 HTML 转义，再只对白名单内的 Markdown 语法做替换，杜绝注入。
 */

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** 行内语法：代码 / 链接 / 加粗 / 删除线 / 斜体 */
function renderInline(text: string): string {
  // 行内代码（最先处理，避免被后续规则污染）
  text = text.replace(/`([^`\n]+)`/g, '<code>$1</code>')
  // 链接 [文字](url)
  text = text.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
  // 加粗 **bold**
  text = text.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
  // 删除线 ~~text~~
  text = text.replace(/~~([^~\n]+)~~/g, '<del>$1</del>')
  // 斜体 *italic*：要求内容首尾非空白，避免误伤乘法或列表星号
  text = text.replace(/(^|[^*])\*([^*\s][^*\n]*?[^*\s])\*(?!\*)/g, '$1<em>$2</em>')
  return text
}

/**
 * 把 Markdown 文本渲染成 HTML 片段。
 *
 * @param md Markdown 原文
 * @returns HTML 字符串；空输入返回空串
 */
export function renderMarkdown(md: string): string {
  if (!md) return ''
  const lines = md.split('\n')
  const out: string[] = []
  let inCode = false
  let codeBuf: string[] = []
  let listType: 'ul' | 'ol' | null = null
  let listBuf: string[] = []

  const flushList = () => {
    if (listType && listBuf.length) {
      out.push(`<${listType}>${listBuf.map((item) => `<li>${item}</li>`).join('')}</${listType}>`)
    }
    listType = null
    listBuf = []
  }

  const closeCode = () => {
    out.push(`<pre><code>${codeBuf.join('\n')}</code></pre>`)
    codeBuf = []
    inCode = false
  }

  for (const rawLine of lines) {
    const line = escapeHtml(rawLine)

    if (inCode) {
      if (line.trimStart().startsWith('```')) closeCode()
      else codeBuf.push(line)
      continue
    }

    if (line.trimStart().startsWith('```')) {
      flushList()
      inCode = true
      codeBuf = []
      continue
    }

    const trimmed = line.trim()
    if (!trimmed) {
      flushList()
      continue
    }

    // 标题
    const heading = /^(#{1,6})\s+(.+)$/.exec(trimmed)
    if (heading) {
      flushList()
      const level = heading[1]!.length
      out.push(`<h${level}>${renderInline(heading[2]!)}</h${level}>`)
      continue
    }

    // 分隔线
    if (/^(-{3,}|\*{3,}|_{3,})$/.test(trimmed)) {
      flushList()
      out.push('<hr />')
      continue
    }

    // 无序列表
    const ul = /^[-*+]\s+(.+)$/.exec(trimmed)
    if (ul) {
      if (listType !== 'ul') {
        flushList()
        listType = 'ul'
      }
      listBuf.push(renderInline(ul[1]!))
      continue
    }

    // 有序列表
    const ol = /^\d+[.)]\s+(.+)$/.exec(trimmed)
    if (ol) {
      if (listType !== 'ol') {
        flushList()
        listType = 'ol'
      }
      listBuf.push(renderInline(ol[1]!))
      continue
    }

    // 引用（> 已被转义为 &gt;）
    if (/^&gt;\s?/.test(trimmed)) {
      flushList()
      out.push(`<blockquote>${renderInline(trimmed.replace(/^&gt;\s?/, ''))}</blockquote>`)
      continue
    }

    // 普通段落
    flushList()
    out.push(`<p>${renderInline(trimmed)}</p>`)
  }

  if (inCode) closeCode()
  flushList()

  return out.join('')
}
