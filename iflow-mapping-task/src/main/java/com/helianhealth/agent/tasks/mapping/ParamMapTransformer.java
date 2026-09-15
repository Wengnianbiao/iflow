package com.helianhealth.agent.tasks.mapping;

import com.helianhealth.agent.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.agent.core.execution.script.ScriptEvaluator;
import com.helianhealth.agent.tasks.mapping.enums.MappingType;
import com.helianhealth.agent.tasks.mapping.enums.ParamType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 根据目标节点行为递归构造 JSON；payload 与输出始终独立。 */
@Component
public class ParamMapTransformer {
    private final GraalJSEvaluator expressionEvaluator;

    public ParamMapTransformer(GraalJSEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    public Map<String, Object> transform(List<ParamMappingConfig> configs, Object payload) {
        validateFields(configs, Set.of(), "$");
        return renderObject(configs, payload, Map.of(), "$");
    }

    private Map<String, Object> renderObject(List<ParamMappingConfig> fields, Object payload,
                                              Map<String, Object> aliases, String path) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (ParamMappingConfig field : safeList(fields)) {
            String key = field.getTargetParamKey();
            result.put(key, render(field, payload, aliases, fieldPath(path, key)));
        }
        return result;
    }

    private Object render(ParamMappingConfig config, Object payload, Map<String, Object> aliases, String path) {
        try {
            return switch (config.getMappingType()) {
                case NAME, CONSTANT, EXPRESSION -> convert(
                        MappingValueResolver.resolve(config, payload, aliases, expressionEvaluator),
                        config.getTargetParamType());
                case OBJECT -> renderObject(config.getChildParams(), payload, aliases, path);
                case ARRAY_MAP -> renderLoop(config, payload, aliases, path);
                case ARRAY_FIXED -> {
                    List<Object> result = new ArrayList<>();
                    List<ParamMappingConfig> items = safeList(config.getItems());
                    for (int i = 0; i < items.size(); i++) {
                        result.add(render(items.get(i), payload, aliases, path + "[" + i + "]"));
                    }
                    yield result;
                }
            };
        } catch (MappingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw error(path, e.getMessage());
        }
    }

    private List<Object> renderLoop(ParamMappingConfig config, Object payload,
                                   Map<String, Object> aliases, String path) {
        Object source = ParamSourceReader.read(payload, aliases, config.getSourceParamKey());
        List<Object> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        if (!(source instanceof List<?> values)) {
            throw new IllegalArgumentException("循环源必须为数组: " + config.getSourceParamKey());
        }
        for (int i = 0; i < values.size(); i++) {
            Map<String, Object> scope = new LinkedHashMap<>(aliases);
            scope.put(config.getItemAlias(), values.get(i));
            result.add(render(config.getItemTemplate(), payload, scope, path + "[" + i + "]"));
        }
        return result;
    }

    private Object convert(Object value, ParamType type) {
        if (value == null) {
            return null;
        }
        if (type == ParamType.OBJECT) {
            if (!(value instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("映射结果必须为对象");
            }
            return value;
        }
        if (type == ParamType.ARRAY) {
            if (!(value instanceof List<?>)) {
                throw new IllegalArgumentException("映射结果必须为数组");
            }
            return value;
        }
        return ParamTypeConverter.convert(value, type);
    }

    // 先验证整棵树，空源数组也不能隐藏无效模板或变量冲突。
    private void validateFields(List<ParamMappingConfig> fields, Set<String> aliases, String path) {
        Set<String> keys = new HashSet<>();
        int i = 0;
        for (ParamMappingConfig field : safeList(fields)) {
            if (field == null || field.getTargetParamKey() == null || field.getTargetParamKey().isBlank()) {
                throw error(path + ".childParams[" + i + "]", "对象字段必须填写 targetParamKey");
            }
            String childPath = fieldPath(path, field.getTargetParamKey());
            if (!keys.add(field.getTargetParamKey())) {
                throw error(childPath, "目标字段重复");
            }
            validateNode(field, aliases, childPath, false);
            i++;
        }
    }

    private void validateNode(ParamMappingConfig config, Set<String> aliases, String path, boolean element) {
        try {
            if (config == null) {
                throw new IllegalArgumentException("元素模板不能为空");
            }
            if (element && config.getTargetParamKey() != null && !config.getTargetParamKey().isBlank()) {
                throw new IllegalArgumentException("数组元素模板不应填写 targetParamKey");
            }
            MappingType mode = config.getMappingType();
            ParamType type = config.getTargetParamType();
            if (mode == null || type == null) {
                throw new IllegalArgumentException("必须指定 mappingType 和有效的 targetParamType");
            }
            if (mode != MappingType.OBJECT && !safeList(config.getChildParams()).isEmpty()
                    || mode != MappingType.ARRAY_FIXED && !safeList(config.getItems()).isEmpty()
                    || mode != MappingType.ARRAY_MAP && (config.getItemTemplate() != null || config.getItemAlias() != null)) {
                throw new IllegalArgumentException("节点包含不适用于当前 mappingType 的结构配置");
            }
            switch (mode) {
                case NAME -> {
                    if (type == ParamType.OBJECT || type == ParamType.ARRAY) {
                        throw new IllegalArgumentException("NAME 仅支持标量目标类型；对象请配置子字段，数组请循环生成或固定组装");
                    }
                    ParamSourceReader.validate(config.getSourceParamKey(), aliases);
                }
                case CONSTANT -> { }
                case EXPRESSION -> {
                    if (!(config.getMappingRule() instanceof String expression) || expression.isBlank()) {
                        throw new IllegalArgumentException("EXPRESSION 的 mappingRule 必须为非空 JS 字符串");
                    }
                }
                case OBJECT -> {
                    requireType(type, ParamType.OBJECT);
                    validateFields(config.getChildParams(), aliases, path);
                }
                case ARRAY_MAP -> {
                    requireType(type, ParamType.ARRAY);
                    ParamSourceReader.validate(config.getSourceParamKey(), aliases);
                    ScriptEvaluator.validateBindingName(config.getItemAlias());
                    Set<String> scope = new HashSet<>(aliases);
                    if (!scope.add(config.getItemAlias())) {
                        throw new IllegalArgumentException("循环变量与外层重名: " + config.getItemAlias());
                    }
                    validateNode(config.getItemTemplate(), scope, path + "[]", true);
                }
                case ARRAY_FIXED -> {
                    requireType(type, ParamType.ARRAY);
                    List<ParamMappingConfig> items = safeList(config.getItems());
                    for (int i = 0; i < items.size(); i++) {
                        validateNode(items.get(i), aliases, path + "[" + i + "]", true);
                    }
                }
            }
        } catch (MappingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw error(path, e.getMessage());
        }
    }

    private static void requireType(ParamType actual, ParamType expected) {
        if (actual != expected) {
            throw new IllegalArgumentException("结构节点的 targetParamType 必须为 " + expected);
        }
    }

    private static List<ParamMappingConfig> safeList(List<ParamMappingConfig> list) {
        return list == null ? List.of() : list;
    }

    private static String fieldPath(String parent, String key) {
        return parent + "." + key;
    }

    private static MappingException error(String path, String message) {
        return new MappingException("MAPPING " + path + ": " + message);
    }

    private static final class MappingException extends IllegalArgumentException {
        private MappingException(String message) {
            super(message);
        }
    }
}
