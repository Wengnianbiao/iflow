package com.helianhealth.agent.tasks.mapping;

import com.helianhealth.agent.common.utils.SpelUtils;
import com.helianhealth.agent.tasks.mapping.enums.MappingType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按 {@link ParamMappingDef} 规则，将 Script Parameters 绑定的源 Map 映射为目标 Map。
 */
final class MappingValueResolver {

    private MappingValueResolver() {}

    static Object resolve(
            ParamMappingDef def,
            Map<String, Object> current,
            Map<String, Object> parent,
            Map<String, Object> root) {
        MappingType mappingType = def.getMappingType();
        if (mappingType == null) {
            mappingType = MappingType.NAME;
        }
        return switch (mappingType) {
            case CONSTANT -> def.getMappingRule();
            case NAME -> ParamSourceReader.read(current, def.getSourceParamKey(), def.getSourceParamType());
            case EXPRESSION ->
                    SpelUtils.parserPreNode(def.getMappingRule(), current, parent, root);
            case BEAN_EXPRESSION ->
                    SpelUtils.parserPreNodeWithBeanAccess(def.getMappingRule(), current, parent, root);
            case SINGLE_MAP -> resolveSingleMap(def, current);
        };
    }

    private static Object resolveSingleMap(ParamMappingDef def, Map<String, Object> current) {
        Object raw = ParamSourceReader.read(current, def.getSourceParamKey(), def.getSourceParamType());
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(def.getTargetParamKey(), map);
        return result;
    }
}
