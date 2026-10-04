import type { TaskValidator, TaskValidationProblem } from '@/pages/definition/validation/types'
import { parseMappingConfigs } from './mappingConfig'

const isRecord = (value: unknown): value is Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value)
const text = (value: unknown) => typeof value === 'string' && Boolean(value.trim())
const expression = (value: unknown) => typeof value === 'string' && value.includes('${')

/** Definition checks only; dynamic expressions are resolved by the iFlow engine. */
export const validateBuiltinTask: TaskValidator = (task) => {
  const problems: TaskValidationProblem[] = []
  const input = task.inputParameters ?? {}
  const add = (path: string, message: string, code = 'TASK_CONFIG') => problems.push({ code, path, message })
  const required = (object: Record<string, unknown>, key: string, path = '/inputParameters') => {
    if (!text(object[key])) add(`${path}/${key}`, `请填写 ${key}`)
  }
  if (task.type === 'HTTP') {
    const nested = isRecord(input.http_request)
    const request = nested ? input.http_request as Record<string, unknown> : input
    const path = nested ? '/inputParameters/http_request' : '/inputParameters'
    if (input.http_request !== undefined && !nested && !expression(input.http_request)) add('/inputParameters/http_request', 'http_request 必须是请求对象或 ${...} 表达式')
    if (!expression(input.http_request)) {
      required(request, 'uri', path); required(request, 'method', path)
      if (text(request.method) && !expression(request.method) && !['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'TRACE'].includes(String(request.method).toUpperCase())) add(`${path}/method`, '请选择有效的 HTTP 方法，或使用 ${...} 表达式')
    }
  }
  if (task.type === 'INLINE') required(input, 'expression')
  if (task.type === 'DO_WHILE' && !text(task.loopCondition)) add('/loopCondition', '请填写循环条件 loopCondition')
  if (task.type === 'SWITCH' || task.type === 'DECISION') {
    if (!text(task.expression)) add('/expression', '请填写分支表达式 expression')
  }
  if (task.type === 'JDBC') for (const key of ['url', 'vendor', 'sqlTemplate']) required(input, key)
  if (task.type === 'MAPPING') {
    try { parseMappingConfigs(input.paramsConfig ?? []) }
    catch (error) { add('/inputParameters/paramsConfig', error instanceof Error ? error.message : '映射配置无效') }
  }
  if (task.type === 'WEBSERVICE') { required(input, 'uri'); required(input, 'soapTemplate') }
  if (task.type === 'SPI') {
    const allowed = ['remoteExtension', 'extensionMode', 'className', 'sourceCode', 'spiInput']
    Object.keys(input).filter((key) => !allowed.includes(key)).forEach((key) => add(`/inputParameters/${key.replace(/~/g, '~0').replace(/\//g, '~1')}`, `SPI 不支持参数 ${key}；业务参数请放入 spiInput`))
    if (!isRecord(input.spiInput) && !(typeof input.spiInput === 'string' && /^\$\{[\s\S]+\}$/.test(input.spiInput.trim()))) add('/inputParameters/spiInput', 'spiInput 必须是 JSON 对象或返回 Map 的 ${...} 表达式')
    const embedded = text(input.className) || text(input.sourceCode)
    const mode = typeof input.extensionMode === 'string' ? input.extensionMode.toUpperCase() : 'ONLINE'
    if (embedded || input.extensionMode === 'ONLINE') {
      required(input, 'remoteExtension'); required(input, 'className')
      if (input.remoteExtension === 'default') add('/inputParameters/remoteExtension', 'default 是保留的扩展名称')
      if (!['ONLINE', 'PROJECT'].includes(mode)) add('/inputParameters/extensionMode', 'extensionMode 必须是 ONLINE 或 PROJECT')
      if (mode === 'ONLINE') required(input, 'sourceCode')
    }
  }
  return problems
}
