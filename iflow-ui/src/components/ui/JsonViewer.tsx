import { useMemo, type ReactNode } from 'react'
import styles from './JsonViewer.module.css'

const JSON_TOKEN_PATTERN = /("(?:\\.|[^"\\])*")(\s*:)?|(-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?)|\b(true|false|null)\b/g

export function serializeJson(value: unknown) {
  try {
    return JSON.stringify(value === undefined ? {} : value, null, 2) ?? '{}'
  } catch {
    return JSON.stringify({ error: '无法序列化此 JSON 数据' }, null, 2)
  }
}

export function JsonViewer({ value, ariaLabel = 'JSON 内容' }: { value: unknown; ariaLabel?: string }) {
  const content = useMemo(() => serializeJson(value), [value])
  const highlightedContent = useMemo(() => highlightJson(content), [content])

  return (
    <pre className={styles.viewer} aria-label={ariaLabel}>
      <code>{highlightedContent}</code>
    </pre>
  )
}

function highlightJson(content: string) {
  const parts: ReactNode[] = []
  let cursor = 0
  let tokenIndex = 0

  for (const match of content.matchAll(JSON_TOKEN_PATTERN)) {
    const offset = match.index ?? 0
    if (offset > cursor) parts.push(content.slice(cursor, offset))

    const className = match[2]
      ? styles.key
      : match[1]
        ? styles.string
        : match[3]
          ? styles.number
          : styles.literal

    parts.push(
      <span className={className} key={`${offset}-${tokenIndex}`}>
        {match[0]}
      </span>,
    )
    cursor = offset + match[0].length
    tokenIndex += 1
  }

  if (cursor < content.length) parts.push(content.slice(cursor))
  return parts
}
