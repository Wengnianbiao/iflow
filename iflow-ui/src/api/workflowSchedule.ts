import axios, { type AxiosRequestConfig } from 'axios'

const SCHEDULE_NAME_PATTERN = /^[A-Za-z0-9_]{1,255}$/

export function isValidScheduleName(name: string) {
  return SCHEDULE_NAME_PATTERN.test(name) && name !== 'preview' && name !== 'new'
}

export type ScheduleConfiguration = {
  name: string
  displayName: string
  cronExpression: string
  zoneId: string
  enabled: boolean
  startWorkflowRequest: {
    route: string
    version?: number | null
    input: Record<string, unknown>
  }
}

export type WorkflowSchedule = ScheduleConfiguration & {
  nextRunAt: string | null
}

const BASE = '/api/workflow/schedules'

async function requestSchedule<T>(config: AxiosRequestConfig, fallback: string): Promise<T> {
  try {
    const { data } = await axios.request<{ code: number; message: string; data: T }>(config)
    if (data.code !== 200 || data.data == null) throw new Error(data.message || fallback)
    return data.data
  } catch (error) {
    if (axios.isAxiosError(error)) {
      if (config.url === BASE && config.method === 'GET' && error.response?.status === 404) {
        throw new Error('定时计划接口不可用，请检查后端版本和定时调度配置。')
      }
      throw new Error(error.response?.data?.message || fallback)
    }
    throw error
  }
}

export function listWorkflowSchedules(signal?: AbortSignal) {
  return requestSchedule<WorkflowSchedule[]>({ method: 'GET', url: BASE, signal }, '加载定时计划失败')
}

export function getWorkflowSchedule(name: string, signal?: AbortSignal) {
  return requestSchedule<WorkflowSchedule>({
    method: 'GET', url: `${BASE}/${encodeURIComponent(name)}`, signal,
  }, '加载定时计划失败')
}

export function previewWorkflowSchedule(cronExpression: string, zoneId: string, signal?: AbortSignal) {
  return requestSchedule<number[]>({
    method: 'GET', url: `${BASE}/preview`, params: { cronExpression, zoneId }, signal,
  }, '计算触发时间失败')
}

export function saveWorkflowSchedule(configuration: ScheduleConfiguration, name?: string) {
  const { name: nextName, displayName, cronExpression, zoneId, enabled, startWorkflowRequest } = configuration
  return requestSchedule<WorkflowSchedule>({
    method: name == null ? 'POST' : 'PUT',
    url: name == null ? BASE : `${BASE}/${encodeURIComponent(name)}`,
    data: { name: nextName, displayName, cronExpression, zoneId, enabled, startWorkflowRequest },
  }, '保存定时计划失败')
}

export async function deleteWorkflowSchedule(name: string): Promise<void> {
  await requestSchedule<boolean>({
    method: 'DELETE', url: `${BASE}/${encodeURIComponent(name)}`,
  }, '删除定时计划失败')
}
