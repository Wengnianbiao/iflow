package com.helianhealth.agent.tasks.parammap;

import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.exception.WorkFlowConfigException;
import com.helianhealth.agent.common.exception.WorkFlowErrorCode;
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

    /**
     * 按配置列表将 {@code source} 转化为目标 Map。
     */
    public Map<String, Object> transform(List<ParamMappingDef> configs, Map<String, Object> source) {
        if (configs == null || configs.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> root = source == null ? Map.of() : source;
        Map<String, Object> target = new LinkedHashMap<>();
        for (ParamMappingDef config : configs) {
            if (config.getTargetParamKey() == null) {
                continue;
            }
            target.put(config.getTargetParamKey(), transformConfig(config, root, root, root));
        }
        return target;
    }

    private Object transformConfig(
            ParamMappingDef config,
            Map<String, Object> current,
            Map<String, Object> parent,
            Map<String, Object> root) {
        ParamType targetType = config.getTargetParamType();
        if (targetType == null) {
            targetType = ParamType.STRING;
        }
        return switch (targetType) {
            case OBJECT -> transformObject(config, current, root);
            case ARRAY, PURE_ARRAY -> transformArray(config, current, root);
            default -> transformScalar(config, current, parent, root, targetType);
        };
    }

    private Map<String, Object> transformObject(
            ParamMappingDef config,
            Map<String, Object> current,
            Map<String, Object> root) {
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
                    transformConfig(child, childSource, childSource, root));
        }
        return result;
    }

    private List<Object> transformArray(
            ParamMappingDef config,
            Map<String, Object> current,
            Map<String, Object> root) {
        List<ParamMappingDef> children = config.getChildParams();
        if (children == null || children.isEmpty()) {
            return List.of();
        }

        Object sourceValue = ParamSourceReader.read(current, config.getSourceParamKey(), config.getSourceParamType());
        if (config.getSourceParamType() == ParamType.NONE) {
            return List.of(transformObject(config, current, root));
        }
        if (config.getSourceParamType() == ParamType.OBJECT && sourceValue != null) {
            return List.of(transformObject(config, ParamSourceReader.asMap(sourceValue), root));
        }
        if (config.getSourceParamType() == ParamType.ARRAY && sourceValue != null) {
            return transformArrayFromSource(config, children, sourceValue, current, root);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Object> transformArrayFromSource(
            ParamMappingDef config,
            List<ParamMappingDef> children,
            Object sourceValue,
            Map<String, Object> current,
            Map<String, Object> root) {
        List<Object> result = new ArrayList<>();
        if (sourceValue instanceof List<?> sourceList) {
            if (sourceList.isEmpty()) {
                return result;
            }
            Object first = sourceList.get(0);
            if (first instanceof Map<?, ?>) {
                for (Object item : sourceList) {
                    Map<String, Object> itemSource = ParamSourceReader.asMap(item);
                    result.add(transformArrayElement(children, itemSource, root));
                }
            } else {
                for (Object item : sourceList) {
                    Map<String, Object> itemSource = new LinkedHashMap<>();
                    itemSource.put(config.getTargetParamKey(), item);
                    result.add(transformArrayElement(children, itemSource, root));
                }
            }
            return result;
        }

        Map<String, Object> itemSource = new LinkedHashMap<>();
        itemSource.put(config.getTargetParamKey(), sourceValue);
        result.add(transformArrayElement(children, itemSource, root));
        return result;
    }

    private Object transformArrayElement(
            List<ParamMappingDef> children,
            Map<String, Object> itemSource,
            Map<String, Object> root) {
        if (children.size() == 1) {
            ParamMappingDef only = children.get(0);
            return transformConfig(only, itemSource, itemSource, root);
        }
        Map<String, Object> element = new LinkedHashMap<>();
        for (ParamMappingDef child : children) {
            if (child.getTargetParamKey() == null) {
                continue;
            }
            element.put(child.getTargetParamKey(), transformConfig(child, itemSource, itemSource, root));
        }
        return element;
    }

    private Object transformScalar(
            ParamMappingDef config,
            Map<String, Object> current,
            Map<String, Object> parent,
            Map<String, Object> root,
            ParamType targetType) {
        Object raw = MappingValueResolver.resolve(config, current, parent, root);
        return ParamTypeConverter.convert(raw, targetType);
    }

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
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
