import request from '@/utils/request';

/**
 * 统一扩展管理 API
 * 支持所有类型的 SPI 扩展（过滤器、接口等）
 * 
 * extensionType 可选值：
 * - 'filter': 过滤器扩展（AgentFilter）
 * - 'remote': 接口扩展（InterfaceRemoteInvoker）
 */

/**
 * 获取指定类型的扩展列表
 * @param {string} extensionType - 扩展类型（filter/remote）
 */
export function getExtensionList(extensionType) {
  return request({
    url: `/api/extension/${extensionType}/list`,
    method: 'get'
  });
}

/**
 * 获取扩展配置详情（带节点参数值）
 * @param {number} codeId - 配置ID
 */
export function getExtensionDetail(codeId) {
  return request({
    url: `/api/extension/detail/${codeId}`,
    method: 'get'
  });
}

/**
 * 注册扩展
 * @param {string} extensionType - 扩展类型（filter/remote）
 * @param {object} data - 扩展配置数据
 */
export function registerExtension(extensionType, data) {
  return request({
    url: `/api/extension/${extensionType}/register`,
    method: 'post',
    data
  });
}

/**
 * 更新扩展
 * @param {string} extensionType - 扩展类型（filter/remote）
 * @param {object} data - 扩展配置数据
 */
export function updateExtension(extensionType, data) {
  return request({
    url: `/api/extension/${extensionType}/update`,
    method: 'post',
    data
  });
}

/**
 * 禁用扩展（所有类型通用）
 * @param {number} codeId - 配置ID
 */
export function disableExtension(codeId) {
  return request({
    url: `/api/extension/disable/${codeId}`,
    method: 'post'
  });
}

/**
 * 刷新扩展（所有类型通用）
 * @param {number} codeId - 配置ID
 */
export function refreshExtension(codeId) {
  return request({
    url: `/api/extension/refresh/${codeId}`,
    method: 'post'
  });
}

/**
 * 获取所有启用的扩展配置
 */
export function getEnabledConfigs() {
  return request({
    url: '/api/extension/enabled-configs',
    method: 'get'
  });
}

/**
 * 获取系统支持的所有 SPI 接口列表
 */
export function getSupportedSpiInterfaces() {
  return request({
    url: '/api/extension/supported-spi-interfaces',
    method: 'get'
  });
}

/**
 * 获取所有支持的扩展类型
 */
export function getSupportedTypes() {
  return request({
    url: '/api/extension/types',
    method: 'get'
  });
}

// ============ 以下为兼容旧 API 的便捷方法 ============

/**
 * 获取过滤器扩展列表（兼容旧 API）
 */
export function getFilterExtensions() {
  return getExtensionList('filter');
}

/**
 * 注册过滤器扩展（兼容旧 API）
 */
export function registerFilterExtension(data) {
  return registerExtension('filter', data);
}

/**
 * 更新过滤器扩展（兼容旧 API）
 */
export function updateFilterExtension(data) {
  return updateExtension('filter', data);
}

/**
 * 获取接口扩展列表（兼容旧 API）
 */
export function getInterfaceExtensions() {
  return getExtensionList('remote');
}

/**
 * 注册接口扩展（兼容旧 API）
 */
export function registerInterfaceExtension(data) {
  return registerExtension('remote', data);
}

/**
 * 更新接口扩展（兼容旧 API）
 */
export function updateInterfaceExtension(data) {
  return updateExtension('remote', data);
}
