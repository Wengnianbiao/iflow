package com.helianhealth.agent.controller.workflow.reponse;

import com.helianhealth.agent.enums.MappingSource;
import com.helianhealth.agent.enums.MappingType;
import com.helianhealth.agent.enums.ParamType;
import com.helianhealth.agent.enums.ProcessType;
import lombok.Data;

import java.util.List;

@Data
public class NodeConfigExportRsp {
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
        private List<NodeParamConfigDTO> childParam;
    }
}
