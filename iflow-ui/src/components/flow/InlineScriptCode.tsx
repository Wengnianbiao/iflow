import type { ReactNode } from 'react'

const FALLBACK_INLINE_SCRIPT = '(function () {\n  return {};\n})();'

const JS_KEYWORDS = new Set([
  'break',
  'case',
  'catch',
  'const',
  'continue',
  'default',
  'do',
  'else',
  'false',
  'finally',
  'for',
  'function',
  'if',
  'let',
  'new',
  'null',
  'return',
  'switch',
  'throw',
  'true',
  'try',
  'typeof',
  'undefined',
  'var',
  'while',
])

function normalizeWhitespace(value: string) {
  return value
    .replace(/\s+/g, ' ')
    .replace(/\s*([{};])\s*/g, '$1')
    .replace(/\s*(\|\||&&|===|!==|==|!=|<=|>=|=|\+|-|\*|\/|<|>)\s*/g, ' $1 ')
    .replace(/\s*,\s*/g, ', ')
    .replace(/\s+/g, ' ')
    .trim()
}

export function formatInlineScript(value: string) {
  const source = value.trim()
  if (!source) return FALLBACK_INLINE_SCRIPT
  if (source.includes('\n')) return source

  const compact = normalizeWhitespace(source)
  let output = ''
  let indent = 0
  let quote: '"' | "'" | '`' | null = null
  let escaped = false
  let parenDepth = 0

  const appendIndent = () => {
    output += '  '.repeat(Math.max(0, indent))
  }

  const trimRight = () => {
    output = output.replace(/[ \t]+$/g, '')
  }

  const newline = () => {
    trimRight()
    if (!output.endsWith('\n')) output += '\n'
    appendIndent()
  }

  for (let index = 0; index < compact.length; index += 1) {
    const char = compact[index]
    const next = compact[index + 1]

    if (quote) {
      output += char
      if (escaped) {
        escaped = false
      } else if (char === '\\') {
        escaped = true
      } else if (char === quote) {
        quote = null
      }
      continue
    }

    if (char === '"' || char === "'" || char === '`') {
      quote = char
      output += char
      continue
    }

    if (char === '(') {
      parenDepth += 1
      output += char
      continue
    }

    if (char === ')') {
      parenDepth = Math.max(0, parenDepth - 1)
      output += char
      continue
    }

    if (char === '{' && next === '}') {
      output += '{}'
      index += 1
      continue
    }

    if (char === '{') {
      trimRight()
      output += ' {'
      indent += 1
      newline()
      continue
    }

    if (char === '}') {
      indent = Math.max(0, indent - 1)
      trimRight()
      if (!output.endsWith('\n')) output += '\n'
      appendIndent()
      output += '}'
      if (next !== ';' && next !== ')' && next !== ',') newline()
      continue
    }

    if (char === ';') {
      output += ';'
      const currentLine = output.split('\n').pop()?.trimStart() ?? ''
      const isForHeader = currentLine.startsWith('for (') || currentLine.startsWith('for(')
      if (!isForHeader && next && next !== ')' && next !== '}') newline()
      continue
    }

    output += char
  }

  return output
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function tokenClass(token: string, previous: string, rest: string) {
  if (token.startsWith('//') || token.startsWith('/*')) return 'token-comment'
  if (/^(['"`])/.test(token)) return 'token-string'
  if (/^\d/.test(token)) return 'token-number'
  if (/^\$/.test(token)) return 'token-variable'
  if (JS_KEYWORDS.has(token)) return 'token-keyword'
  if (/^[A-Za-z_$][\w$]*$/.test(token) && rest.trimStart().startsWith('(') && previous !== '.') return 'token-function'
  return ''
}

export function highlightInlineScriptLine(line: string, keyPrefix: string): ReactNode[] {
  const tokens: ReactNode[] = []
  const pattern = /(\/\/.*|\/\*.*?\*\/|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`|\$[A-Za-z_$][\w$]*|\b\d+(?:\.\d+)?\b|\b[A-Za-z_$][\w$]*\b)/g
  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = pattern.exec(line))) {
    const [token] = match
    if (match.index > lastIndex) tokens.push(line.slice(lastIndex, match.index))
    const previous = line.slice(0, match.index).trimEnd().slice(-1)
    const rest = line.slice(match.index + token.length)
    const className = tokenClass(token, previous, rest)
    tokens.push(className ? <span className={className} key={`${keyPrefix}-${match.index}`}>{token}</span> : token)
    lastIndex = match.index + token.length
  }

  if (lastIndex < line.length) tokens.push(line.slice(lastIndex))
  return tokens
}

export function InlineHighlightedCode({
  code,
  className = '',
  format = true,
}: {
  code: string
  className?: string
  format?: boolean
}) {
  const formatted = format ? formatInlineScript(code) : code
  const lines = formatted.split('\n')
  return (
    <span className={`inline-highlighted-code ${className}`}>
      {lines.map((line, index) => (
        <span className="inline-highlighted-line" key={`${index}-${line}`}>
          {highlightInlineScriptLine(line, `line-${index}`)}
          {index < lines.length - 1 ? '\n' : null}
        </span>
      ))}
    </span>
  )
}
