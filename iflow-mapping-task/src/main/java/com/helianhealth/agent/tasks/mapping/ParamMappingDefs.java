package com.helianhealth.agent.tasks.mapping;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;

import java.util.List;

/**
 * 将工作流 JSON 中的 {@code paramsConfig} 领域化为 {@link ParamMappingDef} 列表。
 */
public final class ParamMappingDefs {

    private ParamMappingDefs() {}

    /** 领域化：JSON / Map 列表 → {@link ParamMappingDef} 树。 */
    @SuppressWarnings("unchecked")
    public static List<ParamMappingDef> toDomain(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof ParamMappingDef) {
            return (List<ParamMappingDef>) list;
        }
        return JSON.parseObject(JSON.toJSONString(raw), new TypeReference<List<ParamMappingDef>>() {});
    }
}
