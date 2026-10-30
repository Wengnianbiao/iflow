export interface StatisticsMetrics {
  totalCount: number
  runningCount: number
  successCount: number
  failureCount: number
  durationCount: number
  avgDurationMs: number | null
  minDurationMs: number | null
  maxDurationMs: number | null
  successRate: number | null
}

export interface StatisticsOverviewParams {
  startTime: number
  endTime: number
  timeZone: string
  workflowRoute?: string
}

export type StatisticsGranularity = 'hour' | 'day' | 'month'

export interface StatisticsTrendPoint {
  startTime: number
  workflowCount: number
  taskCount: number
}

export interface StatisticsOverview {
  workflowCount: number
  taskCount: number
  runtime: {
    workflows: StatisticsMetrics
    tasks: StatisticsMetrics
    granularity: StatisticsGranularity
    trend: StatisticsTrendPoint[]
  }
}
