package com.helianhealth.iflow.tasks.mapping;

import com.helianhealth.iflow.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.iflow.core.execution.script.ScriptEvaluator;

import java.util.Map;

/** 叶子节点求值；结构节点由转换器递归构造。 */
final class MappingValueResolver {
    private MappingValueResolver() {}

    static Object resolve(ParamMappingConfig config, Object payload,
                          Map<String, Object> aliases, GraalJSEvaluator evaluator) {
        return switch (config.getMappingType()) {
            case NAME -> ScriptEvaluator.deepCopy(ParamSourceReader.read(payload, aliases, config.getSourceParamKey()));
            case CONSTANT -> ScriptEvaluator.deepCopy(config.getMappingRule());
            case EXPRESSION -> evaluator.evaluateWithBindings((String) config.getMappingRule(), payload, aliases);
            default -> throw new IllegalArgumentException("结构节点不能按字段取值");
        };
    }
}
