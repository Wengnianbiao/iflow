package com.helianhealth.agent.invocation.remote.resolver;

import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.common.utils.SpelUtils;
import com.helianhealth.agent.invocation.remote.ProxyConvertHelper;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 表达式解析器
 */
@Component
public class ExpressionValueResolver implements ValueResolver {

    @Override
    public Object resolveValue(NodeParamConfig config, Map<String, Object> businessData, Map<String, Object> parentBusinessData, Map<String, Object> sourceBusinessData) {
        Object currentData = ProxyConvertHelper.convertSourceValue(config, businessData);
        ProcessType processType = config.getProcessType();
        if (processType == ProcessType.PRE_PROCESS) {
            return SpelUtils.parserPreNode(config.getMappingRule(), currentData, parentBusinessData, sourceBusinessData);
        }
        return SpelUtils.parserPostNode(config.getMappingRule(), currentData, parentBusinessData, sourceBusinessData);
    }

    @Override
    public boolean supports(MappingType mappingType) {
        return MappingType.EXPRESSION == mappingType;
    }
}
