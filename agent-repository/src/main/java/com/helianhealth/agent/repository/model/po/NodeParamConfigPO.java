package com.helianhealth.agent.repository.model.po;

import com.helianhealth.agent.common.enums.MappingSource;
import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.enums.ProcessType;
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
 * 工作流节点参数前置处理配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "node_param_config")
public class NodeParamConfigPO {

    @Id
    @Column(name = "config_id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer configId;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_type", nullable = false)
    private ProcessType processType;

    @Column(name = "node_id")
    private Integer nodeId;

    @Column(name = "parent_id")
    private Integer parentId;

    @Column(name = "source_param_key")
    private String sourceParamKey;

    @Column(name = "source_param_type")
    @Enumerated(EnumType.STRING)
    private ParamType sourceParamType;

    @Column(name = "param_desc")
    private String paramDesc;

    @Column(name = "target_param_key")
    private String targetParamKey;

    @Column(name = "target_param_type")
    @Enumerated(EnumType.STRING)
    private ParamType targetParamType;

    @Column(name = "keep_single_element_key")
    private Boolean keepSingleElementKey;

    @Column(name = "sort")
    private Integer sort;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_type")
    private MappingType mappingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_source")
    private MappingSource mappingSource;

    @Column(name = "mapping_rule")
    private String mappingRule;
}
