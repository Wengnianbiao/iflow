/** @param {string} spiInterface */
export function getExtensionTypeName(spiInterface) {
  if (spiInterface === 'com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker') return '工作流节点扩展';
  if (spiInterface === 'com.helianhealth.agent.invocation.remote.filter.AgentFilter') return '过滤器扩展';
  if (spiInterface === 'com.helianhealth.agent.api.OpenApiRouteHandler') return 'Open API工作流扩展';
  if (spiInterface === 'com.helianhealth.agent.api.JarvisRouteHandler') return 'Jarvis工作流扩展';
  return '未知类型';
}

/** @param {string} spiInterface */
export function getExtensionTypeTag(spiInterface) {
  if (spiInterface === 'com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker') return 'primary';
  if (spiInterface === 'com.helianhealth.agent.invocation.remote.filter.AgentFilter') return 'success';
  if (spiInterface === 'com.helianhealth.agent.api.OpenApiRouteHandler') return 'warning';
  if (spiInterface === 'com.helianhealth.agent.api.JarvisRouteHandler') return 'danger';
  return '';
}
