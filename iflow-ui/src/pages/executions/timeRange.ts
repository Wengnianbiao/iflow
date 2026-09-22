export const TIME_UNITS = { seconds: '秒', minutes: '分钟', hours: '小时', days: '天', weeks: '周', months: '个月' } as const
export type TimeUnit = keyof typeof TIME_UNITS

export function relativeTimeRange(count: number, unit: TimeUnit, round: boolean, now = Date.now()) {
  if (!Number.isSafeInteger(count) || count <= 0) return null
  const start = new Date(now)
  if (unit === 'months') start.setMonth(start.getMonth() - count)
  else {
    const milliseconds = { seconds: 1000, minutes: 60000, hours: 3600000, days: 86400000, weeks: 604800000 }
    start.setTime(now - count * milliseconds[unit])
  }
  if (round) start.setSeconds(0, 0)
  return Number.isFinite(start.getTime()) && start.getTime() >= 0 ? { from: String(start.getTime()), to: '' } : null
}

export const TIME_PRESETS = [
  { key: 'today', label: '今天' },
  { key: '15m', label: '最近 15 分钟', count: 15, unit: 'minutes' },
  { key: 'yesterday', label: '昨天' },
  { key: '30m', label: '最近 30 分钟', count: 30, unit: 'minutes' },
  { key: '48h', label: '最近 48 小时', count: 48, unit: 'hours' },
  { key: '1h', label: '最近 1 小时', count: 1, unit: 'hours' },
  { key: '72h', label: '最近 72 小时', count: 72, unit: 'hours' },
  { key: '4h', label: '最近 4 小时', count: 4, unit: 'hours' },
  { key: '7d', label: '最近 7 天', count: 7, unit: 'days' },
  { key: '12h', label: '最近 12 小时', count: 12, unit: 'hours' },
] as const

export function presetTimeRange(key: typeof TIME_PRESETS[number]['key'], now = Date.now()) {
  const preset = TIME_PRESETS.find((item) => item.key === key)!
  if ('count' in preset) return relativeTimeRange(preset.count, preset.unit, false, now)!
  const date = new Date(now)
  if (key === 'yesterday') date.setDate(date.getDate() - 1)
  const from = String(date.setHours(0, 0, 0, 0))
  return { from, to: String(date.setHours(23, 59, 59, 999)) }
}
