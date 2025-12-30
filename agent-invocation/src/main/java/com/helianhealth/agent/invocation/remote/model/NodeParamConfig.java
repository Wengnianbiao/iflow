package com.helianhealth.agent.invocation.remote.model;

import com.helianhealth.agent.common.enums.MappingSource;
import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.enums.ProcessType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节点参数配置
 * invocation 模块的参数配置领域模型，用于参数树构建
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeParamConfig {

    /**
     * 参数配置 ID
     */
    private Integer configId;

    /**
     * 处理类型：前置 PRE_PROCESS 或 后置 POST_PROCESS
     */
    private ProcessType processType;

    /**
     * 所属工作流节点 ID
     */
    private Integer nodeId;

    /**
     * 父节点 ID（顶级节点为 null）
     */
    private Integer parentId;

    /**
     * 源参数键名（上游参数名称，如 Jarvis 的参数名）
     */
    private String sourceParamKey;

    /**
     * 源参数类型
     */
    private ParamType sourceParamType;

    /**
     * 参数释义
     */
    private String paramDesc;

    /**
     * 目标参数键名（三方接口所需的参数名称）
     */
    private String targetParamKey;

    /**
     * 目标参数类型（三方接口所需的参数类型）
     */
    private ParamType targetParamType;

    /**
     * 当对象为单个元素时是否保留单个元素 key
     */
    private Boolean keepSingleElementKey;

    /**
     * 排序号（控制参数顺序）
     */
    private Integer sort;

    /**
     * 映射规则类型
     */
    private MappingType mappingType;

    /**
     * 映射来源（INPUT: 入参, RESPONSE: 响应）
     */
    private MappingSource mappingSource;

    /**
     * 映射规则
     * 根据 mappingType 进行解析：
     * - CONSTANT: 固定值
     * - EXPRESSION: SpEL 表达式
     * - NAME: 名称映射后的字段值
     */
    private String mappingRule;
}
