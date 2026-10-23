export type TimePreset = 'today' | 'yesterday' | 'last7Days' | 'custom'

/** 使用本地日历边界，不用固定的 24 小时推算，兼容夏令时。 */
export function presetRange(preset: Exclude<TimePreset, 'custom'>, now = new Date()) {
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const end = new Date(start)
  end.setDate(end.getDate() + (preset === 'yesterday' ? 0 : 1))
  if (preset === 'yesterday') start.setDate(start.getDate() - 1)
  if (preset === 'last7Days') start.setDate(start.getDate() - 6)
  return { startTime: start.getTime(), endTime: end.getTime() - 1 }
}

export function localDateTime(value: number) {
  const date = new Date(value)
  if (!Number.isFinite(value) || Number.isNaN(date.getTime())) return ''
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function statisticsDuration(value: number | null | undefined) {
  if (value == null || !Number.isFinite(value)) return '—'
  return `${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value)} ms`
}

export function statisticsRate(value: number | null | undefined) {
  return value == null ? '—' : `${(value * 100).toFixed(1)}%`
}
