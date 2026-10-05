import { updateTemplateParameter } from './templateParameters'

export type SpiParameterDraft = { originalKey: string; key: string; value: string }

export function readSpiParameterDrafts(value: string, error: string): SpiParameterDraft[] {
  if (!error) return []
  try {
    const parsed: unknown = JSON.parse(value)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return []
    const stored = parsed as Record<string, unknown>
    if (stored.kind !== 'spi-parameters' || !Array.isArray(stored.rows)) return []
    if (!stored.rows.every((row: unknown) => {
      if (!row || typeof row !== 'object' || Array.isArray(row)) return false
      const draft = row as Record<string, unknown>
      return typeof draft.originalKey === 'string'
        && typeof draft.key === 'string' && typeof draft.value === 'string'
    })) return []
    const rows = stored.rows as SpiParameterDraft[]
    return new Set(rows.map((row) => row.originalKey)).size === rows.length ? rows : []
  } catch {
    return []
  }
}

/** 按原参数名应用可用的草稿；待改名行不会覆盖已存在的参数。 */
export function applySpiParameterDrafts(
  parameters: Record<string, unknown>,
  drafts: SpiParameterDraft[],
): { parameters: Record<string, unknown>; drafts: SpiParameterDraft[]; message: string } {
  let nextParameters = parameters
  let pending = drafts.filter((draft) => Object.prototype.hasOwnProperty.call(parameters, draft.originalKey))
  let applied: boolean
  do {
    applied = false
    const remaining: SpiParameterDraft[] = []
    for (const draft of pending) {
      const entries = Object.entries(nextParameters)
      const index = entries.findIndex(([key]) => key === draft.originalKey)
      if (index < 0) continue
      const duplicate = entries.some(([key], row) => row !== index && key === draft.key)
      if (!draft.key.trim() || duplicate) {
        remaining.push(draft)
        continue
      }
      nextParameters = updateTemplateParameter(nextParameters, index, draft)
      applied = true
    }
    pending = remaining
  } while (applied && pending.length)

  const messages = pending.map((draft) => !draft.key.trim()
    ? '参数名不能为空'
    : `参数名 ${draft.key} 已存在，请使用不同的名称`)
  return {
    parameters: nextParameters,
    drafts: pending,
    message: [...new Set(messages)].join('；'),
  }
}
