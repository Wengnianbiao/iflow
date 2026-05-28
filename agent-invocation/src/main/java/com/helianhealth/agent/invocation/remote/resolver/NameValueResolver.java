package com.helianhealth.agent.invocation.remote.resolver;

import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.invocation.remote.ProxyConvertHelper;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NameValueResolver implements ValueResolver {

    @Override
    public Object resolveValue(NodeParamConfig config, Map<String, Object> businessData, Map<String, Object> parentBusinessData, Map<String, Object> sourceBusinessData) {
        if (config.getSourceParamType() == ParamType.NONE) {
            return null;
        }
        return ProxyConvertHelper.convertSourceValue(config, businessData);
    }

    @Override
    public boolean supports(MappingType mappingType) {
        return MappingType.NAME == mappingType;
    }
}
