import request from '@/utils/request'

/**
 * 分页查询Mock配置
 */
export function getMockConfigList(params) {
  return request({
    url: '/v1/console/mock/list',
    method: 'get',
    params
  })
}

/**
 * 根据节点ID查询所有Mock配置
 */
export function getMockConfigsByNodeId(nodeId) {
  return request({
    url: `/v1/console/mock/node/${nodeId}`,
    method: 'get'
  })
}

/**
 * 根据MockID查询Mock配置详情
 */
export function getMockConfigById(mockId) {
  return request({
    url: `/v1/console/mock/${mockId}`,
    method: 'get'
  })
}

/**
 * 创建Mock配置
 */
export function createMockConfig(data) {
  return request({
    url: '/v1/console/mock/create',
    method: 'post',
    data
  })
}

/**
 * 更新Mock配置
 */
export function updateMockConfig(data) {
  return request({
    url: '/v1/console/mock/update',
    method: 'post',
    data
  })
}

/**
 * 删除Mock配置
 */
export function deleteMockConfig(mockId) {
  return request({
    url: `/v1/console/mock/${mockId}`,
    method: 'delete'
  })
}

/**
 * 根据节点ID删除所有Mock配置
 */
export function deleteMockConfigsByNodeId(nodeId) {
  return request({
    url: `/v1/console/mock/node/${nodeId}`,
    method: 'delete'
  })
}

/**
 * 调用Mock接口
 * @param {Array} mockConfigs - Mock配置数组，格式: [{nodeId, mockId}, ...]
 * @param {Object} businessData - 业务参数
 */
export function invokeMock(mockConfigs, businessData) {
  return request({
    url: '/v1/console/mock/invoke',
    method: 'post',
    data: {
      mockConfigs,
      businessData
    }
  })
}
