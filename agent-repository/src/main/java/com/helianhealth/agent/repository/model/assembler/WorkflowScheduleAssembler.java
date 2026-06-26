package com.helianhealth.agent.repository.model.assembler;

import com.helianhealth.agent.repository.model.domain.ScheduleNodeRule;
import com.helianhealth.agent.repository.model.domain.WorkflowSchedule;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowSchedulePO;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowScheduleRulePO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作流调度 PO/Domain 转换器。
 */
public final class WorkflowScheduleAssembler {

    private WorkflowScheduleAssembler() {
    }

    public static WorkflowSchedule toDomain(InterfaceWorkflowSchedulePO po,
                                            List<InterfaceWorkflowScheduleRulePO> rulePOs) {
        if (po == null) {
            return null;
        }
        List<ScheduleNodeRule> rules = rulePOs == null ? null : rulePOs.stream()
                .map(WorkflowScheduleAssembler::toRuleDomain)
                .collect(Collectors.toList());
        return WorkflowSchedule.builder()
                .scheduleId(po.getScheduleId())
                .flowId(po.getFlowId())
                .scheduleName(po.getScheduleName())
                .entryNodeIds(po.getEntryNodeIds())
                .rules(rules)
                .status(po.getStatus())
                .build();
    }

    public static InterfaceWorkflowSchedulePO toPO(WorkflowSchedule schedule) {
        if (schedule == null) {
            return null;
        }
        return InterfaceWorkflowSchedulePO.builder()
                .scheduleId(schedule.getScheduleId())
                .flowId(schedule.getFlowId())
                .scheduleName(schedule.getScheduleName())
                .entryNodeIds(schedule.getEntryNodeIds())
                .status(schedule.getStatus())
                .build();
    }

    public static ScheduleNodeRule toRuleDomain(InterfaceWorkflowScheduleRulePO po) {
        if (po == null) {
            return null;
        }
        return ScheduleNodeRule.builder()
                .ruleId(po.getRuleId())
                .scheduleId(po.getScheduleId())
                .sourceNodeId(po.getSourceNodeId())
                .scheduleExpr(po.getScheduleExpr())
                .paramSourceType(po.getParamSourceType())
                .sort(po.getSort())
                .build();
    }

    public static InterfaceWorkflowScheduleRulePO toRulePO(ScheduleNodeRule rule) {
        if (rule == null) {
            return null;
        }
        return InterfaceWorkflowScheduleRulePO.builder()
                .ruleId(rule.getRuleId())
                .scheduleId(rule.getScheduleId())
                .sourceNodeId(rule.getSourceNodeId())
                .scheduleExpr(rule.getScheduleExpr())
                .paramSourceType(rule.getParamSourceType())
                .sort(rule.getSort())
                .build();
    }
}
