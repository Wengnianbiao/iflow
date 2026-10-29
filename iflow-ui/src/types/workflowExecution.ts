import type { WorkflowDef } from './workflow'

export type WorkflowExecutionStatus =
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'TERMINATED'
  | 'INTERRUPTED'

export type TaskExecutionStatus =
  | 'SCHEDULED'
  | 'IN_PROGRESS'
  | 'CANCELED'
  | 'FAILED'
  | 'FAILED_WITH_TERMINAL_ERROR'
  | 'COMPLETED'
  | 'COMPLETED_WITH_ERRORS'
  | 'TIMED_OUT'
  | 'SKIPPED'

export interface TaskExecution {
  taskId: string
  workflowInstanceId?: string
  referenceTaskName: string
  retryCount?: number
  seq?: number
  status: TaskExecutionStatus
  taskType?: string
  taskDefName?: string
  inputData?: Record<string, unknown>
  outputData?: unknown
  reasonForIncompletion?: string
  scheduledTime?: number
  startTime?: number
  endTime?: number
  updateTime?: number
  workerId?: string
  parentTaskId?: string
}

export interface WorkflowExecution {
  workflowId: string
  status: WorkflowExecutionStatus
  previousStatus?: WorkflowExecutionStatus
  correlationId?: string
  input?: Record<string, unknown>
  output?: unknown
  workflowDefinition?: WorkflowDef
  tasks?: TaskExecution[]
  startTime?: number
  endTime?: number
  updateTime?: number
  reasonForIncompletion?: string
  failedTaskId?: string
}

export interface WorkflowExecutionSummary {
  workflowId: string
  workflowRoute: string
  workflowName?: string
  workflowVersion: number
  status: WorkflowExecutionStatus
  correlationId?: string
  startTime: number
  endTime?: number | null
  updateTime?: number
  durationMs?: number | null
  reasonForIncompletion?: string
  failedTaskId?: string
}

export interface WorkflowExecutionSearchParams {
  workflowRoutes?: string[]
  workflowRoute?: string
  version?: number
  status?: string[]
  workflowId?: string
  correlationId?: string
  startTime?: number
  endTime?: number
  pageNum?: number
  pageSize?: number
}

export interface TaskExecutionSummary {
  taskId: string
  workflowId: string
  workflowRoute: string
  workflowName?: string
  correlationId?: string
  nodeRef: string
  referenceTaskName: string
  taskName?: string
  taskType: string
  async: boolean | null
  status: TaskExecutionStatus
  retryCount: number
  scheduledTime: number
  startTime: number
  endTime: number
  updateTime: number
  durationMs?: number | null
}

export interface TaskExecutionSearchParams {
  workflowRoutes?: string[]
  correlationId?: string
  status?: string[]
  asyncOnly: boolean
  startTime?: number
  endTime?: number
  pageNum: number
  pageSize: number
}

export interface PageResult<T> {
  total: number
  pageNum: number
  pageSize: number
  rows: T[]
}
