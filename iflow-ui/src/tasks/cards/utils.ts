export function cardText(value: unknown) {
  if (value == null) return ''
  return typeof value === 'string' ? value : JSON.stringify(value)
}

export function truncateCardText(value: string, max = 110) {
  return value.length > max ? `${value.slice(0, max)}...` : value
}
