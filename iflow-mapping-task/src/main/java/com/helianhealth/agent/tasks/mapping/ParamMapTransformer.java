package com.helianhealth.agent.tasks.mapping;

import com.helianhealth.agent.common.exception.WorkFlowConfigException;
import com.helianhealth.agent.common.exception.WorkFlowErrorCode;
import com.helianhealth.agent.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.agent.tasks.mapping.enums.MappingType;
import com.helianhealth.agent.tasks.mapping.enums.ParamType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数映射转化器：遍历领域化后的 {@link ParamMappingDef}，将源 {@code Map} 转为目标 {@code Map}。
 *
 * <p>核心流程：
 * <ol>
 *   <li>遍历根级 {@code paramsConfig}</li>
 *   <li>按每条配置的 {@code targetParamType} 递归转化字段</li>
 *   <li>写入目标 Map</li>
 * </ol>
 */
@Component
public class ParamMapTransformer {

    private final GraalJSEvaluator expressionEvaluator;

    public ParamMapTransformer(GraalJSEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    /**
     * 按配置列表将 {@code source} 转化为目标 Map。
     */
    public Map<String, Object> transform(List<ParamMappingDef> configs, Map<String, Object> source) {
        if (configs == null || configs.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> root = source == null ? Map.of() : source;
        Map<String, Object> target = new LinkedHashMap<>();
        Map<String, Object> rootView = new LinkedHashMap<>(root);
        for (ParamMappingDef config : configs) {
            if (config.getTargetParamKey() == null) {
                continue;
            }
            Object value = transformConfig(config, rootView);
            target.put(config.getTargetParamKey(), value);
            rootView.put(config.getTargetParamKey(), value);
        }
        return target;
    }

    private Object transformConfig(ParamMappingDef config, Map<String, Object> current) {
        ParamType targetType = config.getTargetParamType();
        if (targetType == null) {
            targetType = ParamType.STRING;
        }
        MappingType mappingType = config.getMappingType();
        if (mappingType == null) {
            mappingType = MappingType.NAME;
        }
        return switch (mappingType) {
            case CONSTANT, EXPRESSION -> transformResolvedValue(config, current, targetType);
            case NAME -> transformNameMapping(config, current, targetType);
        };
    }

    private Object transformNameMapping(
            ParamMappingDef config, Map<String, Object> current, ParamType targetType) {
        return switch (targetType) {
            case OBJECT -> transformObject(config, current);
            case ARRAY -> transformArray(config, current);
            default -> transformResolvedValue(config, current, targetType);
        };
    }

    private Object transformResolvedValue(
            ParamMappingDef config, Map<String, Object> current, ParamType targetType) {
        Object value = MappingValueResolver.resolve(config, current, expressionEvaluator);
        return switch (targetType) {
            case OBJECT -> requireObject(config, value);
            case ARRAY -> requireArray(config, value);
            default -> ParamTypeConverter.convert(value, targetType);
        };
    }

    private Map<String, Object> transformObject(
            ParamMappingDef config, Map<String, Object> current) {
        Map<String, Object> childSource = resolveObjectSource(config, current);
        Map<String, Object> result = new LinkedHashMap<>();
        List<ParamMappingDef> children = config.getChildParams();
        if (children == null || children.isEmpty()) {
            return result;
        }
        for (ParamMappingDef child : children) {
            if (child.getTargetParamKey() == null) {
                continue;
            }
            result.put(
                    child.getTargetParamKey(),
                    transformConfig(child, childSource));
        }
        return result;
    }

    private List<Object> transformArray(
            ParamMappingDef config, Map<String, Object> current) {
        List<ParamMappingDef> children = config.getChildParams();
        if (children == null || children.isEmpty()) {
            return List.of();
        }

        Object sourceValue = ParamSourceReader.read(current, config.getSourceParamKey(), config.getSourceParamType());
        if (config.getSourceParamType() == ParamType.NONE) {
            return List.of(transformObject(config, current));
        }
        if (config.getSourceParamType() == ParamType.OBJECT && sourceValue != null) {
            return List.of(transformObject(config, ParamSourceReader.asMap(sourceValue)));
        }
        if (config.getSourceParamType() == ParamType.ARRAY && sourceValue != null) {
            return transformArrayFromSource(config, children, sourceValue);
        }
        return List.of();
    }

    private List<Object> requireArray(ParamMappingDef config, Object value) {
        if (!(value instanceof List<?> list)) {
            throw targetTypeError(config, "数组", value);
        }
        return new ArrayList<>(list);
    }

    private Map<String, Object> requireObject(ParamMappingDef config, Object value) {
        if (!(value instanceof Map<?, ?>)) {
            throw targetTypeError(config, "对象", value);
        }
        return ParamSourceReader.asMap(value);
    }

    private WorkFlowConfigException targetTypeError(
            ParamMappingDef config, String expectedType, Object value) {
        String mappingLabel = config.getMappingType() == MappingType.EXPRESSION
                ? "JS表达式"
                : "映射结果";
        String actualType = value == null ? "null" : value.getClass().getSimpleName();
        return new WorkFlowConfigException(
                WorkFlowErrorCode.TYPE_CONVERSION_ERROR,
                mappingLabel
                        + "必须返回"
                        + expectedType
                        + ": "
                        + config.getTargetParamKey()
                        + " (actual="
                        + actualType
                        + ")");
    }

    private List<Object> transformArrayFromSource(
            ParamMappingDef config,
            List<ParamMappingDef> children,
            Object sourceValue) {
        List<Object> result = new ArrayList<>();
        if (sourceValue instanceof List<?> sourceList) {
            if (sourceList.isEmpty()) {
                return result;
            }
            Object first = sourceList.get(0);
            if (first instanceof Map<?, ?>) {
                for (Object item : sourceList) {
                    Map<String, Object> itemSource = ParamSourceReader.asMap(item);
                    result.add(transformArrayElement(children, itemSource));
                }
            } else {
                for (Object item : sourceList) {
                    Map<String, Object> itemSource = new LinkedHashMap<>();
                    itemSource.put(config.getTargetParamKey(), item);
                    result.add(transformArrayElement(children, itemSource));
                }
            }
            return result;
        }

        Map<String, Object> itemSource = new LinkedHashMap<>();
        itemSource.put(config.getTargetParamKey(), sourceValue);
        result.add(transformArrayElement(children, itemSource));
        return result;
    }

    private Object transformArrayElement(
            List<ParamMappingDef> children,
            Map<String, Object> itemSource) {
        if (children.size() == 1) {
            ParamMappingDef only = children.get(0);
            return transformConfig(only, itemSource);
        }
        Map<String, Object> element = new LinkedHashMap<>();
        for (ParamMappingDef child : children) {
            if (child.getTargetParamKey() == null) {
                continue;
            }
            element.put(child.getTargetParamKey(), transformConfig(child, itemSource));
        }
        return element;
    }

    private Map<String, Object> resolveObjectSource(ParamMappingDef config, Map<String, Object> current) {
        ParamType sourceParamType = config.getSourceParamType();
        if (sourceParamType == null || sourceParamType == ParamType.NONE) {
            return current;
        }
        Object sourceValue = ParamSourceReader.read(current, config.getSourceParamKey(), sourceParamType);
        return switch (sourceParamType) {
            case OBJECT -> ParamSourceReader.asMap(sourceValue);
            case ARRAY -> firstMapElement(sourceValue, config.getSourceParamKey());
            default -> current;
        };
    }

    private Map<String, Object> firstMapElement(Object sourceValue, String sourceParamKey) {
        if (!(sourceValue instanceof List<?> list)) {
            throw new WorkFlowConfigException(
                    WorkFlowErrorCode.PRE_PARAM_TREE_BUILD_ERROR,
                    "Expected List for ARRAY source: " + sourceParamKey);
        }
        if (list.isEmpty()) {
            return Map.of();
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?>)) {
            throw new WorkFlowConfigException(
                    WorkFlowErrorCode.PRE_PARAM_TREE_BUILD_ERROR,
                    "First element of array must be a Map: " + sourceParamKey);
        }
        return ParamSourceReader.asMap(first);
    }
}
