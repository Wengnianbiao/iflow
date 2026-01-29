package com.helianhealth.agent.console.controller.reponse;

import com.helianhealth.agent.common.enums.MappingSource;
import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.enums.ProcessType;
import lombok.Data;

import java.util.List;

/**
 * 节点参数导出DTO
 */
@Data
public class NodeParamConfigExportRsp {

    private NodeInfo nodeInfo;

    private List<NodeParamConfigExportDTO> paramsConfig;

    @Data
    public static class NodeInfo {
        private String nodeName;

        private String nodeType;
    }

    @Data
    public static class NodeParamConfigExportDTO {

        private ProcessType processType;
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

        // 子参数列表
        private List<NodeParamConfigExportDTO> childParam;
    }
}
