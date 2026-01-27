import request from '@/utils/request';

/**
 * 接口扩展 API（InterfaceRemoteInvoker）
 * - 单节点绑定
 * - 自动更新节点的 remote_extension 字段
 */

// 获取接口扩展列表
export function getInterfaceExtensions() {
  return request({
    url: '/api/interface-extension/list',
    method: 'get'
  });
}

// 注册接口扩展
export function registerInterfaceExtension(data) {
  return request({
    url: '/api/interface-extension/register',
    method: 'post',
    data
  });
}

// 更新接口扩展
export function updateInterfaceExtension(data) {
  return request({
    url: '/api/interface-extension/update',
    method: 'post',
    data
  });
}

// 禁用接口扩展
export function disableInterfaceExtension(codeId) {
  return request({
    url: `/api/interface-extension/disable/${codeId}`,
    method: 'post'
  });
}

// 刷新接口扩展
export function refreshInterfaceExtension(codeId) {
  return request({
    url: `/api/interface-extension/refresh/${codeId}`,
    method: 'post'
  });
}
