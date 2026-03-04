package com.helianhealth.agent.invocation.remote.param.resolver;

import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.common.enums.MappingType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ConstantValueResolver implements ValueResolver {

    @Override
    public Object resolveValue(NodeParamConfig config, Map<String, Object> businessData, Map<String, Object> parentBusinessData, Map<String, Object> sourceBusinessData) {
        return config.getMappingRule();
    }

    @Override
    public boolean supports(MappingType mappingType) {
        return MappingType.CONSTANT == mappingType;
    }
}
