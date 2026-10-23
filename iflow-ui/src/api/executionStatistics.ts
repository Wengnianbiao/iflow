import axios from 'axios'
import type { StatisticsMode, StatisticsPage, StatisticsParams, TaskStatisticsInstances } from '@/types/executionStatistics'

async function get<T>(path: string, params: object, signal?: AbortSignal): Promise<T> {
  try {
    const { data } = await axios.get<{ code: number; message: string; data: T }>(`/api/workflow/statistics/${path}`, { params, signal })
    if (data.code !== 200 || data.data == null) throw new Error(data.message || '加载调用统计失败')
    return data.data
  } catch (error) {
    if (axios.isAxiosError(error)) throw new Error(error.response?.data?.message || '加载调用统计失败，请检查服务连接')
    throw error
  }
}

export function getExecutionStatistics(mode: StatisticsMode, params: StatisticsParams, signal?: AbortSignal) {
  return get<StatisticsPage>(mode === 'workflow' ? 'workflows' : 'tasks', params, signal)
}

export function getTaskStatisticsInstances(params: StatisticsParams & { nodeRef: string; taskType: string }, signal?: AbortSignal) {
  return get<TaskStatisticsInstances>('task-instances', params, signal)
}
