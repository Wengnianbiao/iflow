package com.helianhealth.agent.tasks.parammap;

import com.alibaba.fastjson2.annotation.JSONField;
import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.common.enums.ParamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * PARAM_MAP 任务映射规则节点（树形 {@link #childParams}）。
 *
 * <p>数组顺序即同级字段顺序。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamMappingDef {

    /** 上游参数键名（源侧支持点号路径钻取嵌套 Map） */
    private String sourceParamKey;

    /** 上游参数类型 */
    private ParamType sourceParamType;

    /** 参数描述 */
    private String paramDesc;

    /** 目标参数键名（嵌套结构用 OBJECT/ARRAY + childParams 表达） */
    private String targetParamKey;

    /** 目标参数类型 */
    private ParamType targetParamType;

    /** 映射规则类型 */
    private MappingType mappingType;

    /** 映射规则内容（常量值、SpEL 表达式等） */
    private String mappingRule;

    /** 子映射（树形嵌套） */
    @Builder.Default
    @JSONField(alternateNames = {"childParam"})
    private List<ParamMappingDef> childParams = new ArrayList<>();
}
