package com.helianhealth.agent.repository.model.domain;

import com.helianhealth.agent.common.enums.MappingSource;
import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.enums.ProcessType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节点参数配置领域模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeParamConfig {

    private Integer configId;

    private ProcessType processType;

    private Integer nodeId;

    private Integer parentId;

    private String sourceParamKey;

    private ParamType sourceParamType;

    private String paramDesc;

    private String targetParamKey;

    private ParamType targetParamType;

    private Boolean keepSingleElementKey;

    private Integer sort;

    private MappingType mappingType;

    private MappingSource mappingSource;

    private String mappingRule;
}
