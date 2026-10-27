import type { WorkflowExecutionSearchParams, WorkflowExecutionStatus } from '@/types/workflowExecution'

export const WORKFLOW_STATUS_OPTIONS: { value: WorkflowExecutionStatus; label: string }[] = [
  { value: 'RUNNING', label: '运行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'FAILED', label: '失败' },
  { value: 'TIMED_OUT', label: '已超时' },
  { value: 'TERMINATED', label: '已终止' },
  { value: 'INTERRUPTED', label: '执行中断' },
]

export type WorkflowSearchFilters = {
  workflowRoutes: string[]
  keyword: string
  status: WorkflowExecutionStatus | ''
  startFrom: string
  startTo: string
}

export type WorkflowSearchState = {
  filters: WorkflowSearchFilters
  page: number
  rowsPerPage: number
}

export function emptySearchFilters(): WorkflowSearchFilters {
  return { workflowRoutes: [], keyword: '', status: '', startFrom: '', startTo: '' }
}

export function normalizeSearchFilters(filters: WorkflowSearchFilters): WorkflowSearchFilters {
  return {
    workflowRoutes: [...new Set(filters.workflowRoutes.map((name) => name.trim()).filter(Boolean))],
    keyword: filters.keyword.trim(),
    status: WORKFLOW_STATUS_OPTIONS.some((option) => option.value === filters.status)
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
  const status = WORKFLOW_STATUS_OPTIONS.find((option) => option.value === params.get('status'))?.value ?? ''
  const requestedSize = positiveInteger(params.get('rowsPerPage'), pageSizeOptions[0])
  return {
    filters: normalizeSearchFilters({
      workflowRoutes: params.getAll('workflowRoute'),
      keyword: params.get('correlationId') ?? '',
      status,
      startFrom: params.get('startFrom') ?? '',
      startTo: params.get('startTo') ?? '',
    }),
    page: positiveInteger(params.get('page'), 1),
    rowsPerPage: pageSizeOptions.includes(requestedSize) ? requestedSize : pageSizeOptions[0],
  }
}

export function writeSearchState(current: URLSearchParams, state: WorkflowSearchState): URLSearchParams {
  const next = new URLSearchParams(current)
  for (const key of ['workflowRoute', 'correlationId', 'status', 'startFrom', 'startTo', 'page', 'rowsPerPage']) {
    next.delete(key)
  }
  const filters = normalizeSearchFilters(state.filters)
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
  const filters = normalizeSearchFilters(state.filters)
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

export function hasSearchFilters(filters: WorkflowSearchFilters) {
  return Boolean(filters.workflowRoutes.length || filters.keyword || filters.status || filters.startFrom || filters.startTo)
}
