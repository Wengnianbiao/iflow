package com.helianhealth.agent.ai.convert;

import com.helianhealth.agent.ai.model.NodeParamConfigMcpResponse;
import com.helianhealth.agent.ai.model.NodeParamConfigMcpView;
import com.helianhealth.agent.common.enums.MappingSource;
import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.repository.model.po.NodeParamConfigDO;

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

    public static NodeParamConfigMcpResponse toMcpResponse(Integer nodeId, String nodeName,
                                                          List<NodeParamConfigMcpView> preParams,
                                                          List<NodeParamConfigMcpView> postParams) {
        int totalCount = countParams(preParams) + countParams(postParams);
        return NodeParamConfigMcpResponse.builder()
                .nodeId(nodeId)
                .nodeName(nodeName)
                .preProcessParams(preParams)
                .postProcessParams(postParams)
                .totalCount(totalCount)
                .build();
    }

    /**
     * 统计参数数量（包括嵌套）
     */
    public static int countParams(List<NodeParamConfigMcpView> params) {
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

    public static NodeParamConfigDO toDO(NodeParamConfigMcpView view, Integer nodeId, Integer parentId) {
        if (view == null) {
            return null;
        }
        return NodeParamConfigDO.builder()
                .processType(parseProcessType(view.getProcessType()))
                .nodeId(nodeId)
                .parentId(parentId)
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
