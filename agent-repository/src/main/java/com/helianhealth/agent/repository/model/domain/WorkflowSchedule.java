package com.helianhealth.agent.repository.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流调度领域模型（聚合根）。
 *
 * <p>调度是独立于「工作流定义」与「节点执行」的第三个领域：</p>
 * <ul>
 *     <li>Workflow —— 流程归属（对外 URI、内容类型、绑定哪张调度图）</li>
 *     <li>WorkflowSchedule —— 本类，负责「从入口到各节点怎么走」</li>
 *     <li>WorkflowNode / Invoker —— 单节点「入参 → 外呼 → 出参」</li>
 * </ul>
 *
 * <p>由它统一承载原先散落在 {@code Workflow.firstFlowNodes} 与
 * {@code WorkflowNode.scheduleExpr / scheduleParamSourceType} 上的调度语义。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowSchedule {

    /**
     * 调度ID（聚合根标识，Workflow 通过该 ID 关联调度图）
     */
    private Integer scheduleId;

    /**
     * 归属工作流 ID（当前 1:1，后续可支持模板复用 / 多版本）
     */
    private Integer flowId;

    /**
     * 调度名称
     */
    private String scheduleName;

    /**
     * 入口节点列表（迁移自 {@code Workflow.firstFlowNodes}）。
     * 多个表示从多入口并行进入。
     */
    private List<Integer> entryNodeIds;

    /**
     * 路由规则集合：当前每个「源节点」一条，描述该节点执行完成后如何路由到下游。
     * 后续可演进为显式边（edge）+ 汇聚（join）模型。
     */
    private List<ScheduleNodeRule> rules;

    /**
     * 状态标识：1-启用，0-禁用
     */
    private Integer status;

    /**
     * 取指定源节点的路由规则（无则返回 null）。
     */
    public ScheduleNodeRule findRuleBySourceNode(Integer sourceNodeId) {
        if (rules == null || sourceNodeId == null) {
            return null;
        }
        return rules.stream()
                .filter(rule -> sourceNodeId.equals(rule.getSourceNodeId()))
                .findFirst()
                .orElse(null);
    }

    public List<ScheduleNodeRule> getRules() {
        if (rules == null) {
            rules = new ArrayList<>();
        }
        return rules;
    }
}
