package com.helianhealth.agent.invocation.remote.resolver;

import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.common.enums.MappingType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NameValueResolver implements ValueResolver {

    @Override
    public Object resolveValue(NodeParamConfig config, Map<String, Object> businessData, Map<String, Object> parentBusinessData, Map<String, Object> sourceBusinessData) {
        return businessData.get(config.getSourceParamKey());
    }

    @Override
    public boolean supports(MappingType mappingType) {
        return MappingType.NAME == mappingType;
    }
}
