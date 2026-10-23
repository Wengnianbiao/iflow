export type StatisticsMode = 'workflow' | 'task'
export type StatisticsSort = 'totalCount' | 'avgDurationMs' | 'minDurationMs' | 'maxDurationMs' | 'successRate'

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

export interface StatisticsRow {
  workflowRoute: string
  workflowName: string | null
  nodeRef: string | null
  taskName: string | null
  taskType: string | null
  metrics: StatisticsMetrics
}

export interface StatisticsParams {
  startTime: number
  endTime: number
  workflowRoute?: string
  asyncOnly: boolean
  sort: StatisticsSort
  descending: boolean
  pageNum: number
  pageSize: number
}

export interface StatisticsPage {
  summary: StatisticsMetrics
  rows: StatisticsRow[]
  total: number
  pageNum: number
  pageSize: number
}

export interface TaskStatisticsInstance {
  taskId: string
  workflowId: string
  referenceTaskName: string
  status: string
  retryCount: number
  startTime: number
  endTime: number
  durationMs: number | null
}

export interface TaskStatisticsInstances {
  rows: TaskStatisticsInstance[]
  total: number
  pageNum: number
  pageSize: number
}
