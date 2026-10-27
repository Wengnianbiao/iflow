export const BODY_FORMATS = [
  { value: 'json', label: 'JSON', contentType: 'application/json', language: 'json' },
  { value: 'xml', label: 'XML', contentType: 'application/xml', language: 'xml' },
  { value: 'text', label: '纯文本', contentType: 'text/plain', language: 'plaintext' },
] as const

export type RequestBodyFormat = (typeof BODY_FORMATS)[number]['value']

export type RequestBodyMode = 'none' | 'form-data' | 'urlencoded' | 'raw'
export type RequestBodyField = { enabled: boolean; name: string; value: string; kind: 'text' | 'file'; file?: File }
export type RequestBody = { mode: RequestBodyMode; text: string; format: RequestBodyFormat; fields: RequestBodyField[] }

export function emptyBodyField(): RequestBodyField {
  return { enabled: true, name: '', value: '', kind: 'text' }
}

export function emptyRequestBody(): RequestBody {
  return { mode: 'raw', text: '{\n}', format: 'json', fields: [emptyBodyField()] }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value)
}

function isGatewayInput(input: Record<string, unknown>) {
  return (
    'body' in input &&
    typeof input.method === 'string' &&
    typeof input.path === 'string' &&
    isRecord(input.query) &&
    isRecord(input.headers)
  )
}

function rawFormat(text: string): RequestBodyFormat {
  if (text.trimStart().startsWith('<')) return 'xml'
  try {
    JSON.parse(text)
    return 'json'
  } catch {
    return 'text'
  }
}

function formFields(body: unknown): RequestBodyField[] {
  if (!isRecord(body)) return []
  return Object.entries(body).flatMap(([name, value]) =>
    (Array.isArray(value) ? value : [value]).map((item) => ({ ...emptyBodyField(), name, value: String(item) })),
  )
}

function xmlFromBody(body: unknown): string {
  if (!isRecord(body) || Object.keys(body).length !== 1) throw new Error('执行记录中的 XML 结构不合法')
  const escape = (value: unknown) =>
    String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&apos;')
  const element = (name: string, value: unknown): string => {
    if (!isRecord(value)) return `<${name}>${value == null ? '' : escape(value)}</${name}>`
    const attributes = Object.entries(value)
      .filter(([key]) => key.startsWith('@'))
      .map(([key, item]) => ` ${key.slice(1)}="${escape(item)}"`)
      .join('')
    const children = Object.entries(value)
      .filter(([key]) => !key.startsWith('@'))
      .map(([key, item]) =>
        key === '#text'
          ? escape(item)
          : (Array.isArray(item) ? item : [item]).map((child) => element(key, child)).join(''),
      )
      .join('')
    return `<${name}${attributes}>${children}</${name}>`
  }
  const [name, value] = Object.entries(body)[0]
  return element(name, value)
}

/** 按保存的 Content-Type 恢复编辑方式；缺少元数据时从原文或入参值识别格式。 */
export function requestBodyFromWorkflowInput(input: unknown): RequestBody {
  if (input === undefined) return emptyRequestBody()
  const wrapped = isRecord(input) && (isGatewayInput(input) || ('body' in input && 'rawBody' in input))
  const body = wrapped ? input.body : input
  const rawBody = wrapped && typeof input.rawBody === 'string' ? input.rawBody : undefined
  const header = wrapped && isRecord(input.headers) ? input.headers['content-type'] : undefined
  const contentType = (Array.isArray(header) ? String(header[0]) : String(header ?? ''))
    .split(';')[0]
    .trim()
    .toLowerCase()
  const result = emptyRequestBody()

  if (wrapped && body == null && rawBody === '') return { ...result, mode: 'none', text: '' }
  if (contentType === 'multipart/form-data' || contentType === 'application/x-www-form-urlencoded') {
    const fields =
      contentType === 'application/x-www-form-urlencoded' && rawBody != null
        ? [...new URLSearchParams(rawBody)].map(([name, value]) => ({ ...emptyBodyField(), name, value }))
        : formFields(body)
    if (wrapped && isRecord(input.files)) {
      for (const [name, descriptors] of Object.entries(input.files)) {
        if (!Array.isArray(descriptors)) throw new Error('执行记录中的文件数据不合法')
        for (const descriptor of descriptors) {
          if (
            !isRecord(descriptor) ||
            typeof descriptor.contentBase64 !== 'string' ||
            typeof descriptor.fileName !== 'string'
          ) {
            throw new Error('执行记录缺少文件内容，无法恢复')
          }
          const bytes = Uint8Array.from(atob(descriptor.contentBase64), (character) => character.charCodeAt(0))
          const file = new File([bytes], descriptor.fileName, { type: String(descriptor.contentType ?? '') })
          fields.push({ ...emptyBodyField(), name, kind: 'file', file })
        }
      }
    }
    return {
      ...result,
      mode: contentType === 'multipart/form-data' ? 'form-data' : 'urlencoded',
      fields: [...fields, emptyBodyField()],
    }
  }

  const xml = contentType.endsWith('/xml') || contentType.endsWith('+xml')
  const text =
    rawBody ??
    (xml && typeof body !== 'string'
      ? xmlFromBody(body)
      : typeof body === 'string' && !contentType.endsWith('/json') && !contentType.endsWith('+json')
        ? body
        : JSON.stringify(body, null, 2))
  const format = xml
    ? 'xml'
    : contentType.endsWith('/json') || contentType.endsWith('+json')
      ? 'json'
      : contentType === 'text/plain'
        ? 'text'
        : rawFormat(text)
  return { ...result, text, format }
}

export function toHttpRequestBody(request: RequestBody): { body: string | FormData; contentType?: string } {
  if (request.mode === 'none') return { body: '' }
  if (request.mode === 'raw')
    return {
      body: request.text,
      contentType: BODY_FORMATS.find((format) => format.value === request.format)!.contentType,
    }
  const fields = request.fields.filter((field) => field.enabled && (field.name || field.value || field.file))
  for (const field of fields) {
    if (!field.name.trim()) throw new Error('请填写表单字段名称')
    if (request.mode === 'form-data' && field.kind === 'file' && !field.file)
      throw new Error(`请为 ${field.name} 选择文件`)
  }
  if (request.mode === 'urlencoded') {
    const form = new URLSearchParams()
    fields.forEach((field) => form.append(field.name, field.value))
    return { body: form.toString(), contentType: 'application/x-www-form-urlencoded' }
  }
  const form = new FormData()
  fields.forEach((field) => form.append(field.name, field.kind === 'file' ? field.file! : field.value))
  return { body: form }
}
