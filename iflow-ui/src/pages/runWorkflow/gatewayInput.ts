export function workflowPath(route: string) {
  const trimmed = route.trim()
  return trimmed.startsWith('/') ? trimmed : `/${trimmed}`
}

/** 与 Open API 网关写入工作流的入参一致。执行页面只编辑 body。 */
export function toGatewayWorkflowInput(workflowRoute: string, body: unknown): Record<string, unknown> {
  return {
    body,
    query: {},
    headers: { 'content-type': 'application/json' },
    files: {},
    method: 'POST',
    path: workflowPath(workflowRoute),
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value)
}

function isGatewayInput(input: Record<string, unknown>) {
  return 'body' in input
    && typeof input.method === 'string'
    && typeof input.path === 'string'
    && isRecord(input.query)
    && isRecord(input.headers)
}

/** 网关入参取出 body；更早的执行记录没有这层包装时，整份入参就是当时填写的内容。 */
export function bodyFromWorkflowInput(input: unknown): unknown {
  if (!isRecord(input)) return input ?? {}
  return isGatewayInput(input) ? input.body ?? {} : input
}

export function formatEditorJson(value: unknown) {
  if (value === undefined) return '{\n}'
  return JSON.stringify(value, null, 2)
}

export function parseEditorJson(text: string): unknown {
  const source = text.trim()
  if (!source) return {}
  return JSON.parse(source) as unknown
}
