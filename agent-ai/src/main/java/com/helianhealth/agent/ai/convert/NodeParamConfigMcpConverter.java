package com.helianhealth.agent.ai.convert;

import com.helianhealth.agent.ai.model.NodeParamConfigMcpResponse;
import com.helianhealth.agent.ai.model.NodeParamConfigMcpView;
import com.helianhealth.agent.common.enums.MappingSource;
import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.workflow.controller.reponse.NodeParamConfigExportRsp;
import com.helianhealth.agent.workflow.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.workflow.model.dto.NodeParamConfigImportDTO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP视图转换器 - 节点参数配置
 * 包含枚举校验和转换逻辑
 */
public class NodeParamConfigMcpConverter {

    // ==================== 枚举校验方法 ====================

    /**
     * 校验并转换 ProcessType
     */
    public static ProcessType parseProcessType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return ProcessType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("processType '%s' 无效，可选值: %s",
                            value, Arrays.toString(ProcessType.values())));
        }
    }

    /**
     * 校验并转换 ParamType
     */
    public static ParamType parseParamType(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return ParamType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("%s '%s' 无效，可选值: %s",
                            fieldName, value, Arrays.toString(ParamType.values())));
        }
    }

    /**
     * 校验并转换 MappingType
     */
    public static MappingType parseMappingType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return MappingType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("mappingType '%s' 无效，可选值: %s",
                            value, Arrays.toString(MappingType.values())));
        }
    }

    /**
     * 校验并转换 MappingSource
     */
    public static MappingSource parseMappingSource(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return MappingSource.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("mappingSource '%s' 无效，可选值: %s",
                            value, Arrays.toString(MappingSource.values())));
        }
    }

    /**
     * 校验单个参数配置（必填项 + 枚举校验 + 递归校验子参数）
     * 必填项：processType, sourceParamType, targetParamKey, targetParamType, mappingType
     * 特殊规则：当sourceParamType为NONE时，sourceParamKey不需要
     */
    public static void validateView(NodeParamConfigMcpView view, int index) {
        String prefix = "params[" + index + "]";
        
        // 1. processType 必填
        if (isBlank(view.getProcessType())) {
            throw new IllegalArgumentException(prefix + ".processType 不能为空");
        }
        parseProcessType(view.getProcessType());
        
        // 2. sourceParamType 必填
        if (isBlank(view.getSourceParamType())) {
            throw new IllegalArgumentException(prefix + ".sourceParamType 不能为空");
        }
        ParamType srcType = parseParamType(view.getSourceParamType(), prefix + ".sourceParamType");
        
        // 3. 当sourceParamType不为NONE时，sourceParamKey必填
        if (srcType != ParamType.NONE && isBlank(view.getSourceParamKey())) {
            throw new IllegalArgumentException(prefix + ".sourceParamKey 不能为空（当sourceParamType不为NONE时）");
        }
        
        // 4. targetParamKey 必填
        if (isBlank(view.getTargetParamKey())) {
            throw new IllegalArgumentException(prefix + ".targetParamKey 不能为空");
        }
        
        // 5. targetParamType 必填
        if (isBlank(view.getTargetParamType())) {
            throw new IllegalArgumentException(prefix + ".targetParamType 不能为空");
        }
        parseParamType(view.getTargetParamType(), prefix + ".targetParamType");
        
        // 6. mappingType 必填
        if (isBlank(view.getMappingType())) {
            throw new IllegalArgumentException(prefix + ".mappingType 不能为空");
        }
        parseMappingType(view.getMappingType());
        
        // mappingSource 非必填，但如果填了要校验
        if (!isBlank(view.getMappingSource())) {
            parseMappingSource(view.getMappingSource());
        }
        
        // 递归校验子参数
        if (view.getChildParams() != null) {
            for (int i = 0; i < view.getChildParams().size(); i++) {
                validateView(view.getChildParams().get(i), i);
            }
        }
    }
    
    private static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 批量校验参数配置列表
     */
    public static void validateViews(List<NodeParamConfigMcpView> views) {
        if (views == null) {
            return;
        }
        for (int i = 0; i < views.size(); i++) {
            validateView(views.get(i), i);
        }
    }

    // ==================== 转换方法 ====================

    /**
     * MCP视图 -> 导入DTO（包含枚举转换）
     */
    public static NodeParamConfigImportDTO.NodeParamConfigDTO toImportDTO(NodeParamConfigMcpView view) {
        if (view == null) {
            return null;
        }
        NodeParamConfigImportDTO.NodeParamConfigDTO dto = new NodeParamConfigImportDTO.NodeParamConfigDTO();
        dto.setProcessType(parseProcessType(view.getProcessType()));
        dto.setSourceParamKey(view.getSourceParamKey());
        dto.setSourceParamType(parseParamType(view.getSourceParamType(), "sourceParamType"));
        dto.setParamDesc(view.getParamDesc());
        dto.setTargetParamKey(view.getTargetParamKey());
        dto.setTargetParamType(parseParamType(view.getTargetParamType(), "targetParamType"));
        dto.setKeepSingleElementKey(view.getKeepSingleElementKey());
        dto.setSort(view.getSort());
        dto.setMappingType(parseMappingType(view.getMappingType()));
        dto.setMappingSource(parseMappingSource(view.getMappingSource()));
        dto.setMappingRule(view.getMappingRule());
        
        // 递归转换子参数
        if (view.getChildParams() != null && !view.getChildParams().isEmpty()) {
            dto.setChildParam(view.getChildParams().stream()
                    .map(NodeParamConfigMcpConverter::toImportDTO)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    /**
     * MCP视图列表 -> 导入DTO列表
     */
    public static List<NodeParamConfigImportDTO.NodeParamConfigDTO> toImportDTOList(List<NodeParamConfigMcpView> views) {
        if (views == null) {
            return new ArrayList<>();
        }
        return views.stream()
                .map(NodeParamConfigMcpConverter::toImportDTO)
                .collect(Collectors.toList());
    }

    /**
     * DO -> MCP视图（枚举转字符串）
     */
    public static NodeParamConfigMcpView toMcpView(NodeParamConfigDO config) {
        if (config == null) {
            return null;
        }
        return NodeParamConfigMcpView.builder()
                .configId(config.getConfigId())
                .processType(config.getProcessType() != null ? config.getProcessType().name() : null)
                .sourceParamKey(config.getSourceParamKey())
                .sourceParamType(config.getSourceParamType() != null ? config.getSourceParamType().name() : null)
                .paramDesc(config.getParamDesc())
                .targetParamKey(config.getTargetParamKey())
                .targetParamType(config.getTargetParamType() != null ? config.getTargetParamType().name() : null)
                .keepSingleElementKey(config.getKeepSingleElementKey())
                .sort(config.getSort())
                .mappingType(config.getMappingType() != null ? config.getMappingType().name() : null)
                .mappingSource(config.getMappingSource() != null ? config.getMappingSource().name() : null)
                .mappingRule(config.getMappingRule())
                .build();
    }

    /**
     * DO列表 -> MCP视图列表（扁平）
     */
    public static List<NodeParamConfigMcpView> toMcpViewList(List<NodeParamConfigDO> configs) {
        if (configs == null) {
            return new ArrayList<>();
        }
        return configs.stream()
                .map(NodeParamConfigMcpConverter::toMcpView)
                .collect(Collectors.toList());
    }

    /**
     * 导出响应 -> MCP响应
     */
    public static NodeParamConfigMcpResponse toMcpResponse(Integer nodeId, NodeParamConfigExportRsp exportRsp) {
        if (exportRsp == null) {
            return NodeParamConfigMcpResponse.builder()
                    .nodeId(nodeId)
                    .totalCount(0)
                    .build();
        }
        
        List<NodeParamConfigExportRsp.NodeParamConfigExportDTO> allParams = exportRsp.getParamsConfig();
        
        // 按处理类型分组
        List<NodeParamConfigMcpView> preParams = new ArrayList<>();
        List<NodeParamConfigMcpView> postParams = new ArrayList<>();
        
        if (allParams != null) {
            for (NodeParamConfigExportRsp.NodeParamConfigExportDTO param : allParams) {
                NodeParamConfigMcpView view = convertExportParam(param);
                if (param.getProcessType() == ProcessType.PRE_PROCESS) {
                    preParams.add(view);
                } else if (param.getProcessType() == ProcessType.POST_PROCESS) {
                    postParams.add(view);
                }
            }
        }
        
        int totalCount = countParams(preParams) + countParams(postParams);
        
        return NodeParamConfigMcpResponse.builder()
                .nodeId(nodeId)
                .nodeName(exportRsp.getNodeInfo() != null ? exportRsp.getNodeInfo().getNodeName() : null)
                .preProcessParams(preParams)
                .postProcessParams(postParams)
                .totalCount(totalCount)
                .build();
    }

    /**
     * 转换导出的参数配置
     */
    private static List<NodeParamConfigMcpView> convertExportParams(List<NodeParamConfigExportRsp.NodeParamConfigExportDTO> params) {
        if (params == null) {
            return new ArrayList<>();
        }
        return params.stream()
                .map(NodeParamConfigMcpConverter::convertExportParam)
                .collect(Collectors.toList());
    }

    private static NodeParamConfigMcpView convertExportParam(NodeParamConfigExportRsp.NodeParamConfigExportDTO param) {
        if (param == null) {
            return null;
        }
        NodeParamConfigMcpView view = NodeParamConfigMcpView.builder()
                .processType(param.getProcessType() != null ? param.getProcessType().name() : null)
                .sourceParamKey(param.getSourceParamKey())
                .sourceParamType(param.getSourceParamType() != null ? param.getSourceParamType().name() : null)
                .paramDesc(param.getParamDesc())
                .targetParamKey(param.getTargetParamKey())
                .targetParamType(param.getTargetParamType() != null ? param.getTargetParamType().name() : null)
                .keepSingleElementKey(param.getKeepSingleElementKey())
                .sort(param.getSort())
                .mappingType(param.getMappingType() != null ? param.getMappingType().name() : null)
                .mappingSource(param.getMappingSource() != null ? param.getMappingSource().name() : null)
                .mappingRule(param.getMappingRule())
                .build();
        
        // 递归转换子参数
        if (param.getChildParam() != null && !param.getChildParam().isEmpty()) {
            view.setChildParams(param.getChildParam().stream()
                    .map(NodeParamConfigMcpConverter::convertExportParam)
                    .collect(Collectors.toList()));
        }
        return view;
    }

    /**
     * 统计参数数量（包括嵌套）
     */
    private static int countParams(List<NodeParamConfigMcpView> params) {
        if (params == null) {
            return 0;
        }
        int count = params.size();
        for (NodeParamConfigMcpView param : params) {
            if (param.getChildParams() != null) {
                count += countParams(param.getChildParams());
            }
        }
        return count;
    }

    /**
     * MCP视图 -> DO（用于更新，包含枚举转换）
     */
    public static NodeParamConfigDO toDO(NodeParamConfigMcpView view) {
        if (view == null) {
            return null;
        }
        return NodeParamConfigDO.builder()
                .configId(view.getConfigId())
                .processType(parseProcessType(view.getProcessType()))
                .sourceParamKey(view.getSourceParamKey())
                .sourceParamType(parseParamType(view.getSourceParamType(), "sourceParamType"))
                .paramDesc(view.getParamDesc())
                .targetParamKey(view.getTargetParamKey())
                .targetParamType(parseParamType(view.getTargetParamType(), "targetParamType"))
                .keepSingleElementKey(view.getKeepSingleElementKey())
                .sort(view.getSort())
                .mappingType(parseMappingType(view.getMappingType()))
                .mappingSource(parseMappingSource(view.getMappingSource()))
                .mappingRule(view.getMappingRule())
                .build();
    }
}
