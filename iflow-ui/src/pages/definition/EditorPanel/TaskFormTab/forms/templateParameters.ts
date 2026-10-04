export function templateParameterText(value: unknown): string {
  return typeof value === 'string' ? value : JSON.stringify(value) ?? ''
}

/** 改名保留原值；只有实际编辑值时才解析 JSON。 */
export function updateTemplateParameter(
  parameters: Record<string, unknown>,
  index: number,
  next: { key: string; value: string },
): Record<string, unknown> {
  const entries = Object.entries(parameters)
  let value = entries[index][1]
  if (next.value !== templateParameterText(value)) {
    try { value = JSON.parse(next.value) } catch { value = next.value }
  }
  entries[index] = [next.key, value]
  return Object.fromEntries(entries)
}
