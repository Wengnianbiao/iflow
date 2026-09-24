package com.helianhealth.iflow.tasks.mapping;

import com.helianhealth.iflow.tasks.mapping.enums.MappingType;
import com.helianhealth.iflow.tasks.mapping.enums.ParamType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * MAPPING 目标结构节点；对象字段、循环元素模板和固定元素均递归使用此类型。
 *
 * <p>数组顺序即同级字段顺序。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamMappingConfig {

    /** payload 或循环变量上的取值路径，例如 $.items、item.code。 */
    private String sourceParamKey;

    /** 参数描述 */
    private String paramDesc;

    /** 目标参数名*/
    private String targetParamKey;

    /** 目标参数类型 */
    private ParamType targetParamType;

    /** 映射规则类型 */
    private MappingType mappingType;

    /** CONSTANT 的原生 JSON 值，或 EXPRESSION 的 JavaScript 字符串。 */
    private Object mappingRule;

    /** OBJECT 的子字段 */
    @Builder.Default
    private List<ParamMappingConfig> childParams = new ArrayList<>();

    /** ARRAY_MAP 当前元素的变量名。 */
    private String itemAlias;

    /** ARRAY_MAP 元素模板 */
    private ParamMappingConfig itemTemplate;

    /** ARRAY_FIXED 的有序元素模板 */
    @Builder.Default
    private List<ParamMappingConfig> items = new ArrayList<>();
}
