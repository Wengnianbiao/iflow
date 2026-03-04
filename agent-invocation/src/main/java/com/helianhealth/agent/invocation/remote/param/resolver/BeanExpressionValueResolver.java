package com.helianhealth.agent.invocation.remote.param.resolver;

import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.common.utils.SpelUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Bean表达式解析器
 */
@Component
public class BeanExpressionValueResolver implements ValueResolver {

    @Override
    public Object resolveValue(NodeParamConfig config, Map<String, Object> businessData, Map<String, Object> parentBusinessData, Map<String, Object> sourceBusinessData) {
        return SpelUtils.parserWithBeanAccess(config.getMappingRule(),
                config.getSourceParamKey() == null ? businessData : businessData.get(config.getSourceParamKey()),
                parentBusinessData,
                sourceBusinessData);
    }

    @Override
    public boolean supports(MappingType mappingType) {
        return MappingType.BEAN_EXPRESSION == mappingType;
    }
}
