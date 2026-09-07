package com.helianhealth.agent.tasks.mapping;

import com.helianhealth.agent.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.agent.tasks.mapping.enums.MappingType;

import java.util.Map;

/**
 * 按 {@link ParamMappingDef} 规则，将 Script Parameters 绑定的源 Map 映射为目标 Map。
 */
final class MappingValueResolver {

    private MappingValueResolver() {}

    static Object resolve(
            ParamMappingDef def,
            Map<String, Object> current,
            GraalJSEvaluator expressionEvaluator) {
        MappingType mappingType = def.getMappingType();
        if (mappingType == null) {
            mappingType = MappingType.NAME;
        }
        return switch (mappingType) {
            case CONSTANT -> def.getMappingRule();
            case NAME -> ParamSourceReader.read(current, def.getSourceParamKey(), def.getSourceParamType());
            case EXPRESSION -> evaluateExpression(def, current, expressionEvaluator);
        };
    }

    private static Object evaluateExpression(
            ParamMappingDef def,
            Map<String, Object> current,
            GraalJSEvaluator expressionEvaluator) {
        if (def.getMappingRule() == null || def.getMappingRule().isBlank()) {
            throw new IllegalArgumentException(
                    "mappingRule is required for EXPRESSION mapping: "
                            + def.getTargetParamKey());
        }
        return expressionEvaluator.evaluate(def.getMappingRule(), current);
    }
}
