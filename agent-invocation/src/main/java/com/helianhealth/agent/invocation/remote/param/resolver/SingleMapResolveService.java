package com.helianhealth.agent.invocation.remote.param.resolver;

import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.common.enums.MappingType;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SingleMapResolveService implements ValueResolver {

    @Override
    public Object resolveValue(NodeParamConfig config, Map<String, Object> businessData, Map<String, Object> parentBusinessData, Map<String, Object> sourceBusinessData) {
        // 返回businessData中第一个元素的value
        if (businessData != null && !businessData.isEmpty()) {
            return businessData.values().iterator().next();
        }
        return null;
    }

    @Override
    public boolean supports(MappingType mappingType) {
        return MappingType.SINGLE_MAP == mappingType;
    }
}
