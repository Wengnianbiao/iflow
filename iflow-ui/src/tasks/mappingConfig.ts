export function countMappingTargetFields(value: unknown): number {
  if (!Array.isArray(value)) return 0

  return value.reduce((count, item) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) return count
    const config = item as Record<string, unknown>
    return count + 1 + countMappingTargetFields(config.childParams)
  }, 0)
}
