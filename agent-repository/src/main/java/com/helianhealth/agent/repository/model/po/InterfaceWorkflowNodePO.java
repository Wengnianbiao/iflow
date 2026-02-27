package com.helianhealth.agent.repository.model.po;

import com.helianhealth.agent.common.enums.NodeType;
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
 * 工作流节点PO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "interface_workflow_node")
public class InterfaceWorkflowNodePO {

    @Id
    @Column(name = "node_id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer nodeId;

    @Column(name = "node_name")
    private String nodeName;

    @Column(name = "flow_id")
    private Integer flowId;

    @Column(name = "param_filter_expr")
    private String paramFilterExpr;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type")
    private NodeType nodeType;

    @Column(name = "meta_info")
    private String metaInfo;

    @Column(name = "split_field")
    private String splitField;

    @Column(name = "schedule_expr")
    private String scheduleExpr;

    @Column(name = "schedule_param_source_type")
    @Enumerated(EnumType.STRING)
    private ScheduleParamSourceType scheduleParamSourceType;

    @Column(name = "keyword_field")
    private String keywordField;

    @Column(name = "remote_extension")
    private String remoteExtension;

    @Column(name = "extension_param")
    private String extensionParam;
}
