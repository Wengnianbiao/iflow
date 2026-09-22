import axios from 'axios'
import type {
  PageResult,
  WorkflowExecution,
  WorkflowExecutionSearchParams,
  WorkflowExecutionSummary,
} from '@/types/workflowExecution'

interface ApiResult<T> {
  code?: number
  message?: string
  data?: T
}

const BASE = '/api/workflow/execution'

function unwrap<T>(payload: ApiResult<T>, fallback: string): T {
  if (payload.code != null && payload.code !== 200) throw new Error(payload.message || fallback)
  if (payload.data == null) throw new Error(payload.message || fallback)
  return payload.data
}

function normalizeError(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as ApiResult<unknown> | undefined
    if (payload?.message) return new Error(payload.message)
    if (error.response?.status) return new Error(`${fallback}（${error.response.status}）`)
  }
  return error instanceof Error ? error : new Error(fallback)
}

export async function searchWorkflowExecutions(
  params: WorkflowExecutionSearchParams,
): Promise<PageResult<WorkflowExecutionSummary>> {
  try {
    const { data } = await axios.get<ApiResult<PageResult<WorkflowExecutionSummary>>>(`${BASE}/search`, {
      paramsSerializer: { indexes: null },
      params: {
        ...params,
        status: params.status?.length ? params.status.join(',') : undefined,
      },
    })
    return unwrap(data, '搜索执行记录失败')
  } catch (error) {
    throw normalizeError(error, '搜索执行记录失败')
  }
}

export async function startWorkflow(
  name: string,
  version: number,
  input: Record<string, unknown>,
): Promise<string> {
  const { data } = await axios.post<ApiResult<string> | { workflowId?: string }>(
    `${BASE}/start`,
    { name, version, input },
  )
  if ('code' in data && data.code != null && data.code !== 200) {
    throw new Error(data.message || '启动工作流失败')
  }
  const workflowId = 'data' in data ? data.data : 'workflowId' in data ? data.workflowId : undefined
  if (!workflowId) throw new Error('工作流已提交，但服务端没有返回实例 ID')
  return workflowId
}

export async function getWorkflowExecution(workflowId: string, includeTasks = true): Promise<WorkflowExecution> {
  try {
    const { data } = await axios.get<ApiResult<WorkflowExecution>>(`${BASE}/${encodeURIComponent(workflowId)}`, {
      params: { includeTasks },
    })
    return unwrap(data, '加载工作流实例失败')
  } catch (error) {
    throw normalizeError(error, '加载工作流实例失败')
  }
}

export async function terminateWorkflow(workflowId: string, reason?: string): Promise<void> {
  try {
    const { data } = await axios.post<ApiResult<boolean>>(
      `${BASE}/${encodeURIComponent(workflowId)}/terminate`,
      null,
      { params: reason ? { reason } : undefined },
    )
    unwrap(data, '终止工作流失败')
  } catch (error) {
    throw normalizeError(error, '终止工作流失败')
  }
}
