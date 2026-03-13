package com.helianhealth.agent.invocation.remote.resolver;

import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.common.utils.SpelUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Bean表达式解析器
 * 支持前置处理和后置处理两种上下文
 */
@Component
public class BeanExpressionValueResolver implements ValueResolver {

    @Override
    public Object resolveValue(NodeParamConfig config, Map<String, Object> businessData, Map<String, Object> parentBusinessData, Map<String, Object> sourceBusinessData) {
        ProcessType processType = config.getProcessType();
        Object currentData = config.getSourceParamKey() == null ? businessData : businessData.get(config.getSourceParamKey());

        if (processType == ProcessType.PRE_PROCESS) {
            // 前置处理：使用 #data, #parent, #source 上下文
            return SpelUtils.parserPreNodeWithBeanAccess(config.getMappingRule(),
                    currentData,
                    parentBusinessData,
                    sourceBusinessData);
        } else {
            // 后置处理：使用 #data, #input, #response 上下文
            // parentBusinessData 在后置处理中对应 inputBusinessData
            // sourceBusinessData 在后置处理中对应 responseBusinessData
            return SpelUtils.parserPostNodeWithBeanAccess(config.getMappingRule(),
                    currentData,
                    parentBusinessData,
                    sourceBusinessData);
        }
    }

    @Override
    public boolean supports(MappingType mappingType) {
        return MappingType.BEAN_EXPRESSION == mappingType;
    }
}
