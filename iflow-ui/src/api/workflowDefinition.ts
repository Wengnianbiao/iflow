import axios from 'axios'
import type { WorkflowDef, WorkflowTask } from '@/types/workflow'

const BASE = '/api/workflow/metadata'

interface ApiResult<T> {
  code?: number
  message?: string
  data?: T
}

export function normalizeWorkflowDef(raw: WorkflowDef & { nodes?: WorkflowTask[]; uniqueName?: string }): WorkflowDef {
  const tasks = raw.tasks?.length ? raw.tasks : (raw.nodes ?? [])
  const name = raw.name ?? raw.uniqueName ?? ''
  return { ...raw, name, tasks }
}

function unwrapResult<T>(payload: ApiResult<T>, fallbackMessage: string): T {
  if (payload.code != null && payload.code !== 200) throw new Error(payload.message || fallbackMessage)
  if (payload.data === undefined) throw new Error(fallbackMessage)
  return payload.data
}

function unwrapVoid(payload: ApiResult<unknown>, fallbackMessage: string): void {
  if (payload.code != null && payload.code !== 200) throw new Error(payload.message || fallbackMessage)
}

function normalizeApiError(error: unknown, fallbackMessage: string): Error {
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as ApiResult<unknown> | undefined
    if (payload?.message) return new Error(payload.message)
    if (error.response?.status) return new Error(`${fallbackMessage} (${error.response.status})`)
  }
  return error instanceof Error ? error : new Error(fallbackMessage)
}

export async function listWorkflowDefinitions(): Promise<WorkflowDef[]> {
  try {
    const { data } = await axios.get<ApiResult<(WorkflowDef & { nodes?: WorkflowTask[] })[]>>(`${BASE}/all`, {
      headers: { 'Cache-Control': 'no-cache', Pragma: 'no-cache' },
    })
    return unwrapResult(data, '加载工作流列表失败').map(normalizeWorkflowDef)
  } catch (error) {
    throw normalizeApiError(error, '加载工作流列表失败')
  }
}

export async function getWorkflowDefinition(name: string, version?: number): Promise<WorkflowDef> {
  const url = version != null ? `${BASE}/detail` : `${BASE}/latest`
  const { data } = await axios.get<ApiResult<WorkflowDef & { nodes?: WorkflowTask[] }>>(url, {
    params: version != null ? { name, version } : { name },
  })
  return normalizeWorkflowDef(unwrapResult(data, `工作流配置不存在: ${name}`))
}

export async function registerWorkflowDefinition(def: WorkflowDef): Promise<WorkflowDef> {
  const { data } = await axios.post<ApiResult<unknown>>(`${BASE}/create`, def)
  unwrapVoid(data, '创建工作流失败')
  return getWorkflowDefinition(def.name, def.version)
}

export async function updateWorkflowDefinition(def: WorkflowDef): Promise<WorkflowDef> {
  const { data } = await axios.put<ApiResult<unknown>>(BASE, def)
  unwrapVoid(data, '更新工作流失败')
  return getWorkflowDefinition(def.name, def.version)
}

export async function saveWorkflowDefinition(def: WorkflowDef): Promise<WorkflowDef> {
  try {
    await getWorkflowDefinition(def.name, def.version)
    return updateWorkflowDefinition(def)
  } catch {
    return registerWorkflowDefinition(def)
  }
}

export async function deleteWorkflowDefinition(name: string, version?: number): Promise<void> {
  try {
    const { data } = await axios.delete<ApiResult<unknown>>(BASE, {
      params: version == null ? { name } : { name, version },
    })
    unwrapVoid(data, '删除工作流失败')
  } catch (error) {
    throw normalizeApiError(error, '删除工作流失败')
  }
}

export async function listWorkflowVersions(name: string): Promise<number[]> {
  try {
    const { data } = await axios.get<ApiResult<number[]>>(`${BASE}/versions`, { params: { name } })
    return unwrapResult(data, '加载工作流版本失败')
  } catch (error) {
    throw normalizeApiError(error, '加载工作流版本失败')
  }
}
