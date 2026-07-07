package com.helianhealth.agent.tasks.parammap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PARAM_MAP 任务入口。
 *
 * <p>转化逻辑：
 * <ol>
 *   <li>领域化 — {@code paramsConfig} JSON → {@link ParamMappingDef} 列表</li>
 *   <li>取源 — 固定 {@code payload} → 源 {@code Map<String, Object>}</li>
 *   <li>遍历转化 — {@link ParamMapTransformer} 按每条配置输出目标 Map</li>
 * </ol>
 */
@Component
@Slf4j
public class ParamMappingEngine {

    public static final String INPUT_PARAMS_CONFIG = "paramsConfig";

    /** 固定的主业务数据键名 */
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

        Map<String, Object> source = requirePayload(input.get(INPUT_PAYLOAD));
        Map<String, Object> target = paramMapTransformer.transform(configs, source);

        log.debug("PARAM_MAP outputKeys={}", target.keySet());
        return target;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requirePayload(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        throw new IllegalArgumentException("payload must resolve to a JSON object");
    }
}
