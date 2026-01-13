package com.helianhealth.agent.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP视图 - 节点参数配置
 * 用于AI接口的输入输出，枚举类型使用String便于校验和提示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeParamConfigMcpView {

    /**
     * 配置ID（查询/更新/删除时使用，导入时不需要）
     */
    private Integer configId;

    /**
     * 处理类型，可选值：PRE_PROCESS(前置处理), POST_PROCESS(后置处理)
     */
    private String processType;

    /**
     * 源参数名（jarvis/调用方的参数名）
     */
    private String sourceParamKey;

    /**
     * 源参数类型，可选值：STRING, INTEGER, LONG, BOOLEAN, OBJECT, ARRAY, PURE_ARRAY, NONE
     */
    private String sourceParamType;

    /**
     * 参数描述/释义
     */
    private String paramDesc;

    /**
     * 目标参数名（三方接口/被调用方的参数名）
     */
    private String targetParamKey;

    /**
     * 目标参数类型，可选值：STRING, INTEGER, LONG, BOOLEAN, OBJECT, ARRAY, PURE_ARRAY, NONE
     */
    private String targetParamType;

    /**
     * 当对象为单个元素时是否保留key
     */
    private Boolean keepSingleElementKey;

    /**
     * 排序号（控制参数顺序）
     */
    private Integer sort;

    /**
     * 映射类型，可选值：CONSTANT(固定值), NAME(名称映射), EXPRESSION(SpEL表达式), BEAN_EXPRESSION(Bean表达式), SINGLE_MAP(单个Map)
     */
    private String mappingType;

    /**
     * 映射来源，可选值：INPUT(来自输入参数), RESPONSE(来自响应数据)
     */
    private String mappingSource;

    /**
     * 映射规则/表达式
     * - NAME: 无需填写，直接按字段名映射
     * - EXPRESSION: SpEL表达式，如 #sex == '男' ? '1' : '2'
     * - CONSTANT: 固定值
     */
    private String mappingRule;

    /**
     * 子参数列表（用于嵌套结构）
     */
    private List<NodeParamConfigMcpView> childParams;
}
