export function spiJavaTemplate(className: string) {
  const separator = className.lastIndexOf('.')
  const packageName = separator > 0 ? className.slice(0, separator) : ''
  const simpleName = separator > 0 ? className.slice(separator + 1) : className
  return `${packageName ? `package ${packageName};\n\n` : ''}import com.helianhealth.agent.tasks.spi.InterfaceRemoteInvoker;

import java.util.LinkedHashMap;
import java.util.Map;

public class ${simpleName} implements InterfaceRemoteInvoker {

    @Override
    public Object remoteInvoke(Map<String, Object> spiInput) {
        Map<String, Object> result = new LinkedHashMap<>(spiInput);
        // TODO 在这里实现当前 SPI 节点的接口业务逻辑
        return result;
    }
}
`
}
