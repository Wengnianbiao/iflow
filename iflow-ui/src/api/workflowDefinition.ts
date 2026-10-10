import axios from 'axios'
import type { WorkflowDef, WorkflowTask } from '@/types/workflow'
import { normalizeWorkflowTags } from '@/utils/workflowTags'

const BASE = '/api/workflow/metadata'

interface ApiResult<T> {
  code?: number
  message?: string
  data?: T
}

export function normalizeWorkflowDef(raw: WorkflowDef & { nodes?: WorkflowTask[] }): WorkflowDef {
  const tasks = raw.tasks?.length ? raw.tasks : (raw.nodes ?? [])
  const route = raw.route ?? ''
  return { ...raw, route, tasks, tags: normalizeWorkflowTags(raw.tags) }
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

export async function getWorkflowDefinition(route: string, version?: number): Promise<WorkflowDef> {
  const url = version != null ? `${BASE}/detail` : `${BASE}/latest`
  const { data } = await axios.get<ApiResult<WorkflowDef & { nodes?: WorkflowTask[] }>>(url, {
    params: version != null ? { route, version } : { route },
  })
  return normalizeWorkflowDef(unwrapResult(data, `工作流配置不存在: ${route}`))
}

export async function registerWorkflowDefinition(def: WorkflowDef): Promise<WorkflowDef> {
  const { data } = await axios.post<ApiResult<unknown>>(`${BASE}/create`, def)
  unwrapVoid(data, '创建工作流失败')
  return getWorkflowDefinition(def.route, def.version)
}

export async function updateWorkflowDefinition(def: WorkflowDef, baseline: WorkflowDef): Promise<WorkflowDef> {
  const { data } = await axios.put<ApiResult<unknown>>(BASE, def, { params: { route: baseline.route, version: baseline.version } })
  unwrapVoid(data, '更新工作流失败')
  return getWorkflowDefinition(def.route, def.version)
}

export async function saveWorkflowDefinition(def: WorkflowDef, baseline?: WorkflowDef): Promise<WorkflowDef> {
  return baseline && def.route === baseline.route && def.version === baseline.version
    ? updateWorkflowDefinition(def, baseline)
    : registerWorkflowDefinition(def)
}

export async function deleteWorkflowDefinition(route: string, version?: number): Promise<void> {
  try {
    const { data } = await axios.delete<ApiResult<unknown>>(BASE, {
      params: version == null ? { route } : { route, version },
    })
    unwrapVoid(data, '删除工作流失败')
  } catch (error) {
    throw normalizeApiError(error, '删除工作流失败')
  }
}

export async function listWorkflowVersions(route: string): Promise<number[]> {
  try {
    const { data } = await axios.get<ApiResult<number[]>>(`${BASE}/versions`, { params: { route } })
    return unwrapResult(data, '加载工作流版本失败')
  } catch (error) {
    throw normalizeApiError(error, '加载工作流版本失败')
  }
}

export async function updateWorkflowTags(route: string, version: number, tags: string[]): Promise<WorkflowDef> {
  try {
    const { data } = await axios.put<ApiResult<WorkflowDef>>(`${BASE}/tags`, normalizeWorkflowTags(tags), {
      params: { route, version },
    })
    return normalizeWorkflowDef(unwrapResult(data, '保存标签失败'))
  } catch (error) {
    throw normalizeApiError(error, '保存标签失败')
  }
}
