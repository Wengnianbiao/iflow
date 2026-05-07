export function isOpenApiStyleInterfaceUri(uri) {
  if (uri == null || uri === '') return false;
  return String(uri).toLowerCase().includes('agent-open-api');
}

/**
 * @param {Array<{ flowId: number, interfaceUri?: string }>} workflowList
 * @param {string} spiType
 * @param {number|null|undefined} selectedFlowId
 */
export function computeWorkflowsForRouteBinding(workflowList, spiType, selectedFlowId) {
  const list = workflowList || [];
  if (spiType !== 'OpenApiRouteHandler' && spiType !== 'JarvisRouteHandler') {
    return list;
  }
  let filtered;
  if (spiType === 'OpenApiRouteHandler') {
    filtered = list.filter(w => isOpenApiStyleInterfaceUri(w.interfaceUri));
  } else {
    filtered = list.filter(w => !isOpenApiStyleInterfaceUri(w.interfaceUri));
  }
  if (selectedFlowId != null) {
    const selected = list.find(w => w.flowId === selectedFlowId);
    if (selected && !filtered.some(w => w.flowId === selectedFlowId)) {
      return filtered.concat([selected]);
    }
  }
  return filtered;
}
