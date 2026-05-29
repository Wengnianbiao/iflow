package com.helianhealth.agent.console.controller.reponse;

import com.helianhealth.agent.common.enums.MappingSource;
import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.common.enums.NodeType;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.common.enums.ScheduleParamSourceType;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

/**
 * 工作流完整导出响应DTO
 * 包含工作流配置 + 关联节点 + 节点参数配置
 * 不包含数据库主键字段
 */
@Data
public class WorkflowFullExportRsp {

    /**
     * 工作流基本信息
     */
    private WorkflowInfo workflowInfo;

    /**
     * 关联的节点列表（包含参数配置）
     */
    private List<NodeFullConfig> nodes;

    @Data
    public static class WorkflowInfo {
        /**
         * 工作流名称
         */
        private String flowName;

        /**
         * 接口URI
         */
        private String interfaceUri;

        /**
         * 入参类型 (JSON/XML)
         */
        private String contentType;

        /**
         * 入口路由扩展
         */
        private String routeExtension;

        /**
         * 入参元信息
         */
        private String contentMetaInfo;

        /**
         * 首节点索引列表（对应nodes数组的索引，导入时重建关联）
         */
        private List<Integer> firstNodeIndexes;

        /**
         * 状态
         */
        private Integer status;
    }

    @Data
    public static class NodeFullConfig {
        /**
         * 节点名称
         */
        private String nodeName;

        /**
         * 节点类型
         */
        private NodeType nodeType;

        /**
         * 参数过滤表达式
         */
        private String paramFilterExpr;

        /**
         * API元数据
         */
        private String metaInfo;

        /**
         * 关键字字段
         */
        private String keywordField;

        /**
         * 分割字段
         */
        private String splitField;

        /**
         * 调度表达式
         */
        private String scheduleExpr;

        /**
         * 调度参数来源类型
         */
        private ScheduleParamSourceType scheduleParamSourceType;

        /**
         * SPI扩展字段
         */
        private String remoteExtension;

        /**
         * 节点参数配置列表（树形结构）
         */
        private List<ParamConfigDTO> paramsConfig;
    }

    @Data
    public static class ParamConfigDTO {
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

        /**
         * 子参数列表
         */
        private List<ParamConfigDTO> childParams;
    }
}
