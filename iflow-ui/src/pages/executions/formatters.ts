import type { WorkflowExecutionSummary } from '@/types/workflowExecution'

export function formatExecutionTime(value?: number | null) {
  if (value == null || value <= 0 || !Number.isFinite(value)) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function formatExecutionDuration(
  execution: Pick<WorkflowExecutionSummary, 'startTime' | 'endTime' | 'durationMs'>,
  now = Date.now(),
) {
  const endTime = execution.endTime && execution.endTime > 0 ? execution.endTime : now
  const value = execution.durationMs ?? (execution.startTime > 0 ? endTime - execution.startTime : null)
  if (value == null || !Number.isFinite(value) || value < 0) return '-'
  if (value < 1000) return `${value} 毫秒`
  const seconds = Math.floor(value / 1000)
  if (seconds < 60) return `${seconds} 秒`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} 分 ${seconds % 60} 秒`
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分`
}
