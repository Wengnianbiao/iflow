export function focusedTaskData(taskType: string, direction: 'input' | 'output', value: unknown): unknown {
  if (value == null || typeof value !== 'object' || Array.isArray(value)) return value
  const data = value as Record<string, unknown>

  if (taskType === 'HTTP' || taskType === 'WEBSERVICE') return data.body

  if (direction === 'output') {
    if (taskType === 'JDBC') {
      return data.rows != null ? data.rows : { updateCount: data.updateCount }
    }
    return value
  }

  switch (taskType) {
    case 'JDBC': return data.sql
    case 'MAPPING': return data.payload
    case 'SPI': return data.spiInput
    case 'INLINE': return Object.fromEntries(Object.entries(data)
      .filter(([key]) => key !== 'expression' && key !== 'evaluatorType'))
    case 'SWITCH':
    case 'DECISION': return Object.fromEntries(Object.entries(data).filter(([key]) => key !== 'hasChildren'))
    default: return value
  }
}
