package com.helianhealth.agent.invocation.remote.param.resolver;

import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ValueResolverSelector {

    private final List<ValueResolver> valueResolvers;

    public ValueResolverSelector(List<ValueResolver> valueResolvers){
        this.valueResolvers = valueResolvers;
    }


    public Object resolveValue(NodeParamConfig config,
                               Map<String, Object> businessData,
                               Map<String, Object> parentBusinessData,
                               Map<String, Object> sourceBusinessData) {
        try {
            return valueResolvers.stream()
                    .filter(resolver -> resolver.supports(config.getMappingType()))
                    .findFirst()
                    .map(resolver -> resolver.resolveValue(config, businessData, parentBusinessData, sourceBusinessData))
                    .orElse(null);
        } catch (Exception e) {
            log.error("目标入参[{}] resolve value error:", config.getTargetParamKey(), e);
            return null;
        }
    }
}
