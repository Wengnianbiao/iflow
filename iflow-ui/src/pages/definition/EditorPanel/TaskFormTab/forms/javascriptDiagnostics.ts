import { parse } from 'acorn'

export type JavaScriptSyntaxProblem = { start: number; end: number; message: string }

/** Parse without executing the script or resolving its runtime bindings. */
export function javaScriptSyntaxProblems(source: string): JavaScriptSyntaxProblem[] {
  try {
    parse(source, { ecmaVersion: 'latest', sourceType: 'script' })
    return []
  } catch (error) {
    if (!(error instanceof SyntaxError) || !('pos' in error) || typeof error.pos !== 'number') throw error
    const position = Math.min(source.length, Math.max(0, error.pos))
    // Mark the preceding character for incomplete code at EOF so the underline remains visible.
    const start = position === source.length ? Math.max(0, source.trimEnd().length - 1) : position
    const raisedAt = 'raisedAt' in error && typeof error.raisedAt === 'number' ? error.raisedAt : position + 1
    const lineEnd = source.indexOf('\n', start)
    const end = Math.min(lineEnd < 0 ? source.length : lineEnd, Math.max(start + 1, raisedAt))
    return [{ start, end, message: `JavaScript 语法错误：${error.message.replace(/ \(\d+:\d+\)$/, '')}` }]
  }
}
