package com.helianhealth.agent.repository.model.def;

import com.helianhealth.agent.common.enums.MappingSource;
import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.common.enums.ParamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 参数映射定义（PRE/POST 字段映射的树形节点）。
 *
 * <p>对应原 {@code node_param_config} 表的一行，但不再使用全局 {@code config_id / parent_id} 维护父子关系，
 * 而是直接以 {@link #childParams} 内嵌成树，整体随工作流定义一并序列化进 {@code json_data}。
 * 是否属于 PRE / POST 由其所在的 {@link NodeDef#getPreParams()} / {@link NodeDef#getPostParams()} 列表决定，
 * 因此这里不再保留 {@code processType} 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamMappingDef {

    /**
     * 上游参数键名（源侧支持点号路径钻取嵌套 Map）
     */
    private String sourceParamKey;

    /**
     * 上游参数类型
     */
    private ParamType sourceParamType;

    /**
     * 参数描述
     */
    private String paramDesc;

    /**
     * 目标参数键名（禁止含点号，嵌套结构用 OBJECT/ARRAY + childParams 表达）
     */
    private String targetParamKey;

    /**
     * 目标参数类型
     */
    private ParamType targetParamType;

    /**
     * 单元素数组是否保留外层 key
     */
    private Boolean keepSingleElementKey;

    /**
     * 同级排序号
     */
    private Integer sort;

    /**
     * 映射规则类型
     */
    private MappingType mappingType;

    /**
     * 映射数据源（仅 POST 有意义）
     */
    private MappingSource mappingSource;

    /**
     * 映射规则内容（常量值、SpEL 表达式等）
     */
    private String mappingRule;

    /**
     * 子映射（树形嵌套）
     */
    @Builder.Default
    private List<ParamMappingDef> childParams = new ArrayList<>();
}
