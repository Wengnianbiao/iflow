export function isXml(value: unknown): value is string {
  return typeof value === 'string'
    && /^\s*<(?:\?xml\b|!--|[\w:.-]+(?:\s|\/?>))/.test(value) && />\s*$/.test(value)
}

type XmlElement = { open: string; close: string; children: Array<XmlElement | string> }
const XML_TOKENS = /<!--[\s\S]*?-->|<!\[CDATA\[[\s\S]*?\]\]>|<\?[\s\S]*?\?>|<!DOCTYPE(?:[^>"'\[]|"[^"]*"|'[^']*'|\[[\s\S]*?\])*>|<(?:[^<>"']|"[^"]*"|'[^']*')+>|[^<]+/g

/** Only add display indentation to element-only content; keep mixed text and CDATA intact. */
export function formatXml(content: string): string {
  const tokens = content.match(XML_TOKENS) ?? []
  if (tokens.join('') !== content) return content
  const root: XmlElement = { open: '', close: '', children: [] }
  const stack = [root]
  for (const token of tokens) {
    const parent = stack[stack.length - 1]
    if (/^<\//.test(token)) {
      const name = /^<\/([^\s>]+)\s*>$/.exec(token)?.[1]
      if (stack.length === 1 || name !== /^<([^\s/>]+)/.exec(parent.open)?.[1]) return content
      parent.close = token
      stack.pop()
    } else if (/^<[\w:.-]+(?:\s|\/?>)/.test(token) && !/\/\s*>$/.test(token)) {
      const element: XmlElement = { open: token, close: '', children: [] }
      parent.children.push(element)
      stack.push(element)
    } else {
      parent.children.push(token)
    }
  }
  if (stack.length !== 1) return content

  const raw = (node: XmlElement | string): string => typeof node === 'string'
    ? node : node.open + node.children.map(raw).join('') + node.close
  const render = (node: XmlElement | string, depth: number): string => {
    const indent = '  '.repeat(depth)
    if (typeof node === 'string') return indent + node
    const hasText = node.children.some((child) => typeof child === 'string'
      && (child.startsWith('<![CDATA[') || (!child.startsWith('<') && child.trim().length > 0)))
    if (hasText || /\bxml:space\s*=\s*(['"])preserve\1/.test(node.open)) return indent + raw(node)
    const children = node.children.filter((child) => typeof child !== 'string' || child.trim())
    if (!children.length) return indent + raw(node)
    return `${indent}${node.open}\n${children.map((child) => render(child, depth + 1)).join('\n')}\n${indent}${node.close}`
  }
  return root.children.filter((child) => typeof child !== 'string' || child.trim())
    .map((child) => render(child, 0)).join('\n')
}
