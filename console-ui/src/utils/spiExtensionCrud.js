import {
  disableFilterExtension,
  disableWorkflowExtension,
  disableWorkflowNodeExtension,
  deleteFilterExtension,
  deleteWorkflowExtension,
  deleteWorkflowNodeExtension,
  enableFilterExtension,
  enableWorkflowExtension,
  enableWorkflowNodeExtension,
  getFilterExtensionDetail,
  getWorkflowExtensionDetail,
  getWorkflowNodeExtensionDetail
} from '@/api/extension';

/** @param {{ spiInterface: string, configId: number }} row */
export function loadExtensionDetailRequest(row) {
  if (row.spiInterface === 'com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker') {
    return getWorkflowNodeExtensionDetail(row.configId);
  }
  if (row.spiInterface === 'com.helianhealth.agent.invocation.remote.filter.AgentFilter') {
    return getFilterExtensionDetail(row.configId);
  }
  return getWorkflowExtensionDetail(row.configId);
}

/** @param {{ spiInterface: string, configId: number }} row */
export function enableExtensionRequest(row) {
  if (row.spiInterface === 'com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker') {
    return enableWorkflowNodeExtension(row.configId);
  }
  if (row.spiInterface === 'com.helianhealth.agent.invocation.remote.filter.AgentFilter') {
    return enableFilterExtension(row.configId);
  }
  return enableWorkflowExtension(row.configId);
}

/** @param {{ spiInterface: string, configId: number }} row */
export function disableExtensionRequest(row) {
  if (row.spiInterface === 'com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker') {
    return disableWorkflowNodeExtension(row.configId);
  }
  if (row.spiInterface === 'com.helianhealth.agent.invocation.remote.filter.AgentFilter') {
    return disableFilterExtension(row.configId);
  }
  return disableWorkflowExtension(row.configId);
}

/** @param {{ spiInterface: string, configId: number }} row */
export function deleteExtensionRequest(row) {
  if (row.spiInterface === 'com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker') {
    return deleteWorkflowNodeExtension(row.configId);
  }
  if (row.spiInterface === 'com.helianhealth.agent.invocation.remote.filter.AgentFilter') {
    return deleteFilterExtension(row.configId);
  }
  return deleteWorkflowExtension(row.configId);
}
