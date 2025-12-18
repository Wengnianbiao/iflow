package com.helianhealth.agent.invocation.remote.resolver;

import com.helianhealth.agent.workflow.enums.MappingType;
import com.helianhealth.agent.workflow.enums.ProcessType;
import com.helianhealth.agent.workflow.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.workflow.utils.ExpressionMapperUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 表达式解析器
 */
@Component
public class ExpressionValueResolver implements ValueResolver {

    @Override
    public Object resolveValue(NodeParamConfigDO config, Map<String, Object> businessData, Map<String, Object> parentBusinessData, Map<String, Object> sourceBusinessData) {
        ProcessType processType = config.getProcessType();
        if (processType == ProcessType.PRE_PROCESS) {
            return ExpressionMapperUtils.parser(config.getMappingRule(),
                    config.getSourceParamKey() == null ? businessData : businessData.get(config.getSourceParamKey()),
                    parentBusinessData,
                    sourceBusinessData);
        } else {
            return ExpressionMapperUtils.parserPostNode(config.getMappingRule(),
                    config.getSourceParamKey() == null ? businessData : businessData.get(config.getSourceParamKey()),
                    parentBusinessData,
                    sourceBusinessData);
        }

    }

    @Override
    public boolean supports(MappingType mappingType) {
        return MappingType.EXPRESSION == mappingType;
    }
}
