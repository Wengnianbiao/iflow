package com.helianhealth.agent.tasks.spi;

import java.util.Collections;
import java.util.Map;

/**
 * 默认远程调用扩展：不做任何处理，直接返回空 Map。
 */
public class DefaultInterfaceRemoteInvoker implements InterfaceRemoteInvoker {

    @Override
    public Map<String, Object> remoteInvoke(Map<String, Object> input) {
        return Collections.emptyMap();
    }
}
