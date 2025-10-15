package com.helianhealth.agent.remote.resolver;

import com.helianhealth.agent.model.domain.NodeParamConfigDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ValueResolversWrapper {

    private final List<ValueResolver> valueResolvers;

    public ValueResolversWrapper(List<ValueResolver> valueResolvers){
        this.valueResolvers = valueResolvers;
    }


    public Object resolveValue(NodeParamConfigDO config, Map<String, Object> businessData, Map<String, Object> sourceBusinessData) {
        return valueResolvers.stream()
                .filter(resolver -> resolver.supports(config.getMappingType()))
                .findFirst()
                .map(resolver -> resolver.resolveValue(config, businessData, sourceBusinessData))
                .orElse(null);
    }
}
