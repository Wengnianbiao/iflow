import request from '@/utils/request';

/**
 * 扩展类型 code 常量，与后端 com.helianhealth.agent.console.enums.ExtensionType 保持一致。
 */
export const EXTENSION_TYPE = {
  FILTER: 'filter',
  REMOTE: 'remote',
  WORKFLOW: 'workflow',
  JARVIS_WORKFLOW: 'jarvis-workflow'
};

/**
 * 扩展类型列表/注册/更新：按 type 参数分派到后端通用接口。
 */
export function listExtensionsByType(type) {
  return request({
    url: `/api/extension/${type}/list`,
    method: 'get'
  });
}

export function registerExtensionByType(type, data) {
  return request({
    url: `/api/extension/${type}/register`,
    method: 'post',
    data
  });
}

export function updateExtensionByType(type, data) {
  return request({
    url: `/api/extension/${type}/update`,
    method: 'post',
    data
  });
}

/**
 * 通用 CRUD 接口（所有扩展类型共用，不区分 type）。
 */
export function getExtensionDetail(configId) {
  return request({
    url: `/api/extension/detail/${configId}`,
    method: 'get'
  });
}

export function enableExtension(configId) {
  return request({
    url: `/api/extension/enable/${configId}`,
    method: 'post'
  });
}

export function disableExtension(configId) {
  return request({
    url: `/api/extension/disable/${configId}`,
    method: 'post'
  });
}

export function deleteExtension(configId) {
  return request({
    url: `/api/extension/delete/${configId}`,
    method: 'post'
  });
}

// ==================== Filter 扩展 ====================

export function getFilterExtensions() {
  return listExtensionsByType(EXTENSION_TYPE.FILTER);
}

export function registerFilterExtension(data) {
  return registerExtensionByType(EXTENSION_TYPE.FILTER, data);
}

export function updateFilterExtension(data) {
  return updateExtensionByType(EXTENSION_TYPE.FILTER, data);
}

export function getFilterExtensionDetail(configId) {
  return getExtensionDetail(configId);
}

export function enableFilterExtension(configId) {
  return enableExtension(configId);
}

export function disableFilterExtension(configId) {
  return disableExtension(configId);
}

export function deleteFilterExtension(configId) {
  return deleteExtension(configId);
}

// ==================== 工作流节点扩展（Invoker / Remote）====================

export function getInterfaceExtensions() {
  return listExtensionsByType(EXTENSION_TYPE.REMOTE);
}

export function registerInterfaceExtension(data) {
  return registerExtensionByType(EXTENSION_TYPE.REMOTE, data);
}

export function updateInterfaceExtension(data) {
  return updateExtensionByType(EXTENSION_TYPE.REMOTE, data);
}

export function getWorkflowNodeExtensionDetail(configId) {
  return getExtensionDetail(configId);
}

export function enableWorkflowNodeExtension(configId) {
  return enableExtension(configId);
}

export function disableWorkflowNodeExtension(configId) {
  return disableExtension(configId);
}

export function deleteWorkflowNodeExtension(configId) {
  return deleteExtension(configId);
}

// ==================== 工作流路由扩展（OpenApi / Jarvis）====================

export function getWorkflowRouteExtensions() {
  return listExtensionsByType(EXTENSION_TYPE.WORKFLOW);
}

export function getJarvisWorkflowRouteExtensions() {
  return listExtensionsByType(EXTENSION_TYPE.JARVIS_WORKFLOW);
}

export function registerWorkflowRouteExtension(data) {
  return registerExtensionByType(EXTENSION_TYPE.WORKFLOW, data);
}

export function registerJarvisWorkflowRouteExtension(data) {
  return registerExtensionByType(EXTENSION_TYPE.JARVIS_WORKFLOW, data);
}

export function updateWorkflowRouteExtension(data) {
  return updateExtensionByType(EXTENSION_TYPE.WORKFLOW, data);
}

export function updateJarvisWorkflowRouteExtension(data) {
  return updateExtensionByType(EXTENSION_TYPE.JARVIS_WORKFLOW, data);
}

export function getWorkflowExtensionDetail(configId) {
  return getExtensionDetail(configId);
}

export function enableWorkflowExtension(configId) {
  return enableExtension(configId);
}

export function disableWorkflowExtension(configId) {
  return disableExtension(configId);
}

export function deleteWorkflowExtension(configId) {
  return deleteExtension(configId);
}
