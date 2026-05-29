package com.helianhealth.agent.console.model.dto;

import com.helianhealth.agent.common.enums.MappingSource;
import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.enums.ProcessType;
import lombok.Data;

import java.util.List;

@Data
public class NodeConfigImportDTO {
    private NodeInfo nodeInfo;
    private List<NodeParamConfigDTO> paramsConfig;

    /**
     * 导入节点支持的节点信息，可按需拓展
     */
    @Data
    public static class NodeInfo {

        private String nodeName;

        private String nodeType;

        private String metaInfo;

        private String paramFilterExpr;

        private String keywordField;

        private String splitField;
    }

    @Data
    public static class NodeParamConfigDTO {

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
        private List<NodeParamConfigDTO> childParams;
    }
}
