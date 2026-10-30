import axios from 'axios'
import type { StatisticsOverview, StatisticsOverviewParams } from '@/types/executionStatistics'

export async function getStatisticsOverview(params: StatisticsOverviewParams, signal?: AbortSignal): Promise<StatisticsOverview> {
  try {
    const { data } = await axios.get<{ code: number; message: string; data: StatisticsOverview }>(
      '/api/workflow/statistics/overview', { params, signal },
    )
    if (data.code !== 200 || data.data == null) throw new Error(data.message || '加载调度统计失败')
    return data.data
  } catch (error) {
    if (axios.isAxiosError(error)) throw new Error(error.response?.data?.message || '加载调度统计失败，请检查服务连接')
    throw error
  }
}
