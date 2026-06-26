package com.helianhealth.agent.repository.model.po;

import com.helianhealth.agent.common.enums.ScheduleParamSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 工作流调度节点路由规则 PO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "interface_workflow_schedule_rule")
public class InterfaceWorkflowScheduleRulePO {

    @Id
    @Column(name = "rule_id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer ruleId;

    @Column(name = "schedule_id")
    private Integer scheduleId;

    @Column(name = "source_node_id")
    private Integer sourceNodeId;

    @Column(name = "schedule_expr")
    private String scheduleExpr;

    @Column(name = "schedule_param_source_type")
    @Enumerated(EnumType.STRING)
    private ScheduleParamSourceType paramSourceType;

    @Column(name = "sort")
    private Integer sort;
}
