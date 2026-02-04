import request from '@/utils/request'

/**
 * 获取调用日志列表
 */
export function fetchLogList(params) {
  return request({
    url: '/v1/console/api-log/all',
    method: 'get',
    params
  })
}

/**
 * 获取所有节点（无分页）
 */
export function fetchNodeList() {
  return request({
    url: '/v1/console/node/all-unpaged',
    method: 'get'
  })
}

/**
 * 获取今日统计数据
 */
export function fetchTodayStats() {
  return request({
    url: '/v1/console/api-log/stats/today',
    method: 'get'
  })
}

/**
 * 获取最近7天统计数据
 */
export function fetchLast7DaysStats() {
  return request({
    url: '/v1/console/api-log/stats/last7days',
    method: 'get'
  })
}