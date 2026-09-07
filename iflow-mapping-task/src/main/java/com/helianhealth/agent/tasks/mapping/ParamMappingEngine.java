package com.helianhealth.agent.tasks.mapping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MAPPING 任务入口。
 *
 * <p>转化逻辑：
 * <ol>
 *   <li>领域化 — {@code paramsConfig} JSON → {@link ParamMappingDef} 列表</li>
 *   <li>取源 — 合并除 {@code paramsConfig} 外的输入参数为源 {@code Map<String, Object>}</li>
 *   <li>遍历转化 — {@link ParamMapTransformer} 按每条配置输出目标 Map</li>
 * </ol>
 */
@Component
@Slf4j
public class ParamMappingEngine {

    public static final String INPUT_PARAMS_CONFIG = "paramsConfig";

    public static final String INPUT_PAYLOAD = "payload";

    private final ParamMapTransformer paramMapTransformer;

    public ParamMappingEngine(ParamMapTransformer paramMapTransformer) {
        this.paramMapTransformer = paramMapTransformer;
    }

    public Map<String, Object> map(Map<String, Object> input) {
        List<ParamMappingDef> configs = ParamMappingDefs.toDomain(input.get(INPUT_PARAMS_CONFIG));
        if (configs.isEmpty()) {
            throw new IllegalArgumentException("paramsConfig is required");
        }

        Map<String, Object> source = mergeMappingInputs(input);
        Map<String, Object> target = paramMapTransformer.transform(configs, source);

        log.debug("MAPPING outputKeys={}", target.keySet());
        return target;
    }

    private static Map<String, Object> mergeMappingInputs(Map<String, Object> input) {
        boolean hasMappingInput =
                input.keySet().stream().anyMatch(key -> !INPUT_PARAMS_CONFIG.equals(key));
        if (!hasMappingInput) {
            throw new IllegalArgumentException("at least one mapping input is required");
        }

        Map<String, Object> source = new LinkedHashMap<>();
        Object legacyPayload = input.get(INPUT_PAYLOAD);
        if (legacyPayload instanceof Map<?, ?> payload) {
            payload.forEach((key, value) -> source.put(String.valueOf(key), value));
        }

        input.forEach((key, value) -> {
            if (!INPUT_PARAMS_CONFIG.equals(key) && !INPUT_PAYLOAD.equals(key)) {
                source.put(key, value);
            }
        });
        if (input.containsKey(INPUT_PAYLOAD)) {
            source.put(INPUT_PAYLOAD, legacyPayload);
        }

        return source;
    }
}
