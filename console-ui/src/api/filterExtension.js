import request from '@/utils/request';

/**
 * 过滤器扩展 API（AgentFilter）
 * - 多节点绑定
 * - 自动更新节点的 extension_param 字段
 */

// 获取过滤器扩展列表
export function getFilterExtensions() {
  return request({
    url: '/api/filter-extension/list',
    method: 'get'
  });
}

// 注册过滤器扩展
export function registerFilterExtension(data) {
  return request({
    url: '/api/filter-extension/register',
    method: 'post',
    data
  });
}

// 更新过滤器扩展
export function updateFilterExtension(data) {
  return request({
    url: '/api/filter-extension/update',
    method: 'post',
    data
  });
}

// 禁用过滤器扩展
export function disableFilterExtension(codeId) {
  return request({
    url: `/api/filter-extension/disable/${codeId}`,
    method: 'post'
  });
}

// 刷新过滤器扩展
export function refreshFilterExtension(codeId) {
  return request({
    url: `/api/filter-extension/refresh/${codeId}`,
    method: 'post'
  });
}
