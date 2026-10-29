import type { TaskExecutionSearchParams, TaskExecutionStatus, WorkflowExecutionSearchParams, WorkflowExecutionStatus } from '@/types/workflowExecution'

export const WORKFLOW_STATUS_OPTIONS: { value: WorkflowExecutionStatus; label: string }[] = [
  { value: 'RUNNING', label: '运行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'FAILED', label: '失败' },
  { value: 'TIMED_OUT', label: '已超时' },
  { value: 'TERMINATED', label: '已终止' },
  { value: 'INTERRUPTED', label: '执行中断' },
]

export const TASK_STATUS_OPTIONS: { value: TaskExecutionStatus; label: string }[] = [
  { value: 'SCHEDULED', label: '等待执行' },
  { value: 'IN_PROGRESS', label: '执行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'COMPLETED_WITH_ERRORS', label: '完成但有错误' },
  { value: 'FAILED', label: '失败' },
  { value: 'FAILED_WITH_TERMINAL_ERROR', label: '终止性失败' },
  { value: 'TIMED_OUT', label: '已超时' },
  { value: 'CANCELED', label: '已取消' },
  { value: 'SKIPPED', label: '已跳过' },
]

export type ExecutionSearchMode = 'workflow' | 'task'

export type WorkflowSearchFilters = {
  workflowRoutes: string[]
  keyword: string
  status: WorkflowExecutionStatus | TaskExecutionStatus | ''
  startFrom: string
  startTo: string
}

export type WorkflowSearchState = {
  mode: ExecutionSearchMode
  asyncOnly: boolean
  filters: WorkflowSearchFilters
  page: number
  rowsPerPage: number
}

export function emptySearchFilters(): WorkflowSearchFilters {
  return { workflowRoutes: [], keyword: '', status: '', startFrom: '', startTo: '' }
}

export function normalizeSearchFilters(filters: WorkflowSearchFilters, mode: ExecutionSearchMode): WorkflowSearchFilters {
  const statusOptions = mode === 'task' ? TASK_STATUS_OPTIONS : WORKFLOW_STATUS_OPTIONS
  return {
    workflowRoutes: [...new Set(filters.workflowRoutes.map((name) => name.trim()).filter(Boolean))],
    keyword: filters.keyword.trim(),
    status: statusOptions.some((option) => option.value === filters.status)
      ? filters.status : '',
    startFrom: normalizeTimestamp(filters.startFrom),
    startTo: normalizeTimestamp(filters.startTo),
  }
}

function normalizeTimestamp(value: string | undefined) {
  if (!value?.trim()) return ''
  const time = Number(value)
  return Number.isSafeInteger(time) && time >= 0 && time <= 8640000000000000 ? String(time) : ''
}

function positiveInteger(value: string | null, fallback: number) {
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : fallback
}

/** Workflow routes identify selections; pagination follows the console conventions. */
export function readSearchState(params: URLSearchParams, pageSizeOptions: readonly number[]): WorkflowSearchState {
  const mode = params.get('view') === 'task' ? 'task' : 'workflow'
  const statusOptions = mode === 'task' ? TASK_STATUS_OPTIONS : WORKFLOW_STATUS_OPTIONS
  const status = statusOptions.find((option) => option.value === params.get('status'))?.value ?? ''
  const requestedSize = positiveInteger(params.get('rowsPerPage'), pageSizeOptions[0])
  return {
    mode,
    asyncOnly: params.get('asyncOnly') === 'true',
    filters: normalizeSearchFilters({
      workflowRoutes: params.getAll('workflowRoute'),
      keyword: params.get('correlationId') ?? '',
      status,
      startFrom: params.get('startFrom') ?? '',
      startTo: params.get('startTo') ?? '',
    }, mode),
    page: positiveInteger(params.get('page'), 1),
    rowsPerPage: pageSizeOptions.includes(requestedSize) ? requestedSize : pageSizeOptions[0],
  }
}

export function writeSearchState(current: URLSearchParams, state: WorkflowSearchState): URLSearchParams {
  const next = new URLSearchParams(current)
  for (const key of ['view', 'asyncOnly', 'workflowRoute', 'correlationId', 'status', 'startFrom', 'startTo', 'page', 'rowsPerPage']) {
    next.delete(key)
  }
  if (state.mode === 'task') next.set('view', 'task')
  if (state.asyncOnly) next.set('asyncOnly', 'true')
  const filters = normalizeSearchFilters(state.filters, state.mode)
  for (const name of filters.workflowRoutes) next.append('workflowRoute', name)
  if (filters.keyword) next.set('correlationId', filters.keyword)
  if (filters.status) next.set('status', filters.status)
  if (filters.startFrom) next.set('startFrom', filters.startFrom)
  if (filters.startTo) next.set('startTo', filters.startTo)
  next.set('page', String(state.page))
  next.set('rowsPerPage', String(state.rowsPerPage))
  return next
}

/** Only applied filters reach the iFlow API; paging never submits the draft form. */
export function buildSearchParams(state: WorkflowSearchState): WorkflowExecutionSearchParams {
  const filters = normalizeSearchFilters(state.filters, state.mode)
  return {
    workflowRoutes: filters.workflowRoutes.length ? filters.workflowRoutes : undefined,
    correlationId: filters.keyword || undefined,
    status: filters.status ? [filters.status] : undefined,
    // The backend otherwise defaults an omitted lower bound to seven days.
    ...(filters.startFrom || filters.startTo ? { startTime: Number(filters.startFrom || 0) } : {}),
    ...(filters.startTo ? { endTime: Number(filters.startTo) } : {}),
    pageNum: state.page,
    pageSize: state.rowsPerPage,
  }
}

export function buildTaskSearchParams(state: WorkflowSearchState): TaskExecutionSearchParams {
  const params = buildSearchParams(state)
  return {
    workflowRoutes: params.workflowRoutes,
    correlationId: params.correlationId,
    status: params.status,
    asyncOnly: state.asyncOnly,
    startTime: params.startTime,
    endTime: params.endTime,
    pageNum: state.page,
    pageSize: state.rowsPerPage,
  }
}

export function hasSearchFilters(filters: WorkflowSearchFilters) {
  return Boolean(filters.workflowRoutes.length || filters.keyword || filters.status || filters.startFrom || filters.startTo)
}
