package com.helianhealth.agent.invocation.remote.resolver;

import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.invocation.remote.model.NodeParamConfig;

import java.util.Map;

/**
 * 值解析器策略接口
 */
public interface ValueResolver {

    Object resolveValue(NodeParamConfig config,
                        Map<String, Object> businessData,
                        Map<String, Object> parentBusinessData,
                        Map<String, Object> sourceBusinessData);

    boolean supports(MappingType mappingType);
}
