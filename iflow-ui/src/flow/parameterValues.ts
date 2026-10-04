export type EditableParameter = { key: string; value: string; originalValue?: unknown }
const displayValue = (value: unknown): string => value == null ? '' : typeof value === 'string' ? value : JSON.stringify(value)

export function editableParameterRows(values: Record<string, unknown>, excluded: string[] = []): EditableParameter[] {
  return Object.entries(values).filter(([key]) => !excluded.includes(key)).map(([key, originalValue]) => ({ key, value: displayValue(originalValue), originalValue }))
}

/** Editing a key or a sibling must not stringify untouched JSON values. */
export function parameterValues(rows: EditableParameter[]): Record<string, unknown> {
  return Object.fromEntries(rows.map((row) => [row.key,
    Object.prototype.hasOwnProperty.call(row, 'originalValue') && row.value === displayValue(row.originalValue) ? row.originalValue : row.value,
  ]))
}
