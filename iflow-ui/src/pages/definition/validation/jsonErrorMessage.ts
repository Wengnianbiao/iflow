export function jsonErrorMessage(error: unknown, field: string) {
  if (error instanceof SyntaxError) {
    return `${field} JSON 格式不正确，${/end of JSON|end of input/i.test(error.message) ? '内容尚未写完整，请检查括号或值' : '请检查引号、逗号和括号'}`
  }
  return error instanceof Error ? error.message : `${field} JSON 格式不正确`
}
