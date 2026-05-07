import request from '@/utils/request';

export function getFilterExtensions() {
  return request({
    url: '/api/extension/filter/list',
    method: 'get'
  });
}

export function registerFilterExtension(data) {
  return request({
    url: '/api/extension/filter/register',
    method: 'post',
    data
  });
}

export function updateFilterExtension(data) {
  return request({
    url: '/api/extension/filter/update',
    method: 'post',
    data
  });
}

export function getInterfaceExtensions() {
  return request({
    url: '/api/extension/workflow-node/list',
    method: 'get'
  });
}

export function registerInterfaceExtension(data) {
  return request({
    url: '/api/extension/workflow-node/register',
    method: 'post',
    data
  });
}

export function updateInterfaceExtension(data) {
  return request({
    url: '/api/extension/workflow-node/update',
    method: 'post',
    data
  });
}

export function getWorkflowRouteExtensions() {
  return request({
    url: '/api/extension/workflow/open-api/list',
    method: 'get'
  });
}

export function getJarvisWorkflowRouteExtensions() {
  return request({
    url: '/api/extension/workflow/jarvis/list',
    method: 'get'
  });
}

export function registerWorkflowRouteExtension(data) {
  return request({
    url: '/api/extension/workflow/open-api/register',
    method: 'post',
    data
  });
}

export function registerJarvisWorkflowRouteExtension(data) {
  return request({
    url: '/api/extension/workflow/jarvis/register',
    method: 'post',
    data
  });
}

export function updateWorkflowRouteExtension(data) {
  return request({
    url: '/api/extension/workflow/open-api/update',
    method: 'post',
    data
  });
}

export function updateJarvisWorkflowRouteExtension(data) {
  return request({
    url: '/api/extension/workflow/jarvis/update',
    method: 'post',
    data
  });
}

export function getWorkflowExtensionDetail(configId) {
  return request({
    url: `/api/extension/workflow/detail/${configId}`,
    method: 'get'
  });
}

export function enableWorkflowExtension(configId) {
  return request({
    url: `/api/extension/workflow/enable/${configId}`,
    method: 'post'
  });
}

export function disableWorkflowExtension(configId) {
  return request({
    url: `/api/extension/workflow/disable/${configId}`,
    method: 'post'
  });
}

export function deleteWorkflowExtension(configId) {
  return request({
    url: `/api/extension/workflow/delete/${configId}`,
    method: 'post'
  });
}

export function getWorkflowNodeExtensionDetail(configId) {
  return request({
    url: `/api/extension/workflow-node/detail/${configId}`,
    method: 'get'
  });
}

export function enableWorkflowNodeExtension(configId) {
  return request({
    url: `/api/extension/workflow-node/enable/${configId}`,
    method: 'post'
  });
}

export function disableWorkflowNodeExtension(configId) {
  return request({
    url: `/api/extension/workflow-node/disable/${configId}`,
    method: 'post'
  });
}

export function deleteWorkflowNodeExtension(configId) {
  return request({
    url: `/api/extension/workflow-node/delete/${configId}`,
    method: 'post'
  });
}

export function getFilterExtensionDetail(configId) {
  return request({
    url: `/api/extension/filter/detail/${configId}`,
    method: 'get'
  });
}

export function enableFilterExtension(configId) {
  return request({
    url: `/api/extension/filter/enable/${configId}`,
    method: 'post'
  });
}

export function disableFilterExtension(configId) {
  return request({
    url: `/api/extension/filter/disable/${configId}`,
    method: 'post'
  });
}

export function deleteFilterExtension(configId) {
  return request({
    url: `/api/extension/filter/delete/${configId}`,
    method: 'post'
  });
}

export function getWorkflowExtensionBindings() {
  return request({
    url: '/api/extension/workflow/binding/list',
    method: 'get'
  });
}

export function updateWorkflowExtensionBinding(data) {
  return request({
    url: '/api/extension/workflow/binding/update',
    method: 'post',
    data
  });
}
