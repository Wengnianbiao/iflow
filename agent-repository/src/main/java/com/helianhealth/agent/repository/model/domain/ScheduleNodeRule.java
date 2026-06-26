package com.helianhealth.agent.repository.model.domain;

import com.helianhealth.agent.common.enums.ScheduleParamSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 调度节点路由规则。
 *
 * <p>描述「某个源节点执行完成后，依据其响应如何路由到下游」，
 * 迁移自 {@code WorkflowNode.scheduleExpr / scheduleParamSourceType}。</p>
 *
 * <p>当前形态是「按源节点」一条规则（与既有 SpEL 三元返回 {@code List<Integer>} 对齐）；
 * 后续可平滑演进为「显式边 + 条件」以及「汇聚（join）策略」。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleNodeRule {

    /**
     * 规则ID
     */
    private Integer ruleId;

    /**
     * 归属调度ID
     */
    private Integer scheduleId;

    /**
     * 源节点ID：该节点执行完成后触发本路由规则。
     */
    private Integer sourceNodeId;

    /**
     * 调度表达式（SpEL）。
     * 变量 {@code #data} = 源节点响应 Map；返回值须为 {@code List<Integer>}（下游 nodeId）或 {@code null}。
     */
    private String scheduleExpr;

    /**
     * 下游节点入参来源：
     * {@code ORIGINAL} 用最初业务入参，{@code PRE_RESPONSE} 用本节点响应。
     */
    private ScheduleParamSourceType paramSourceType;

    /**
     * 排序号（同源节点多规则时的优先级，预留；当前单规则填 0）
     */
    private Integer sort;
}
