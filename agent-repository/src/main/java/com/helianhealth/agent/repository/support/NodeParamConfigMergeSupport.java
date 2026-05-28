package com.helianhealth.agent.repository.support;

import com.helianhealth.agent.repository.model.domain.NodeParamConfig;

/**
 * 节点参数配置更新合并：在已有记录上叠加本次变更，null 表示未传（保留原值）。
 * Console 全量提交时会带上 nodeId，此时 parentId 等字段的 null 表示有意清空。
 */
public final class NodeParamConfigMergeSupport {

    private NodeParamConfigMergeSupport() {
    }

    public static NodeParamConfig mergeForUpdate(NodeParamConfig existing, NodeParamConfig incoming) {
        if (existing == null) {
            return incoming;
        }
        if (incoming == null) {
            return existing;
        }
        boolean fullUpdate = incoming.getNodeId() != null;
        return NodeParamConfig.builder()
                .configId(incoming.getConfigId())
                .nodeId(incoming.getNodeId() != null ? incoming.getNodeId() : existing.getNodeId())
                .parentId(fullUpdate ? incoming.getParentId() : coalesce(incoming.getParentId(), existing.getParentId()))
                .processType(coalesce(incoming.getProcessType(), existing.getProcessType()))
                .sourceParamKey(coalesce(incoming.getSourceParamKey(), existing.getSourceParamKey()))
                .sourceParamType(coalesce(incoming.getSourceParamType(), existing.getSourceParamType()))
                .paramDesc(coalesce(incoming.getParamDesc(), existing.getParamDesc()))
                .targetParamKey(coalesce(incoming.getTargetParamKey(), existing.getTargetParamKey()))
                .targetParamType(coalesce(incoming.getTargetParamType(), existing.getTargetParamType()))
                .keepSingleElementKey(coalesce(incoming.getKeepSingleElementKey(), existing.getKeepSingleElementKey()))
                .sort(coalesce(incoming.getSort(), existing.getSort()))
                .mappingType(coalesce(incoming.getMappingType(), existing.getMappingType()))
                .mappingSource(coalesce(incoming.getMappingSource(), existing.getMappingSource()))
                .mappingRule(coalesce(incoming.getMappingRule(), existing.getMappingRule()))
                .build();
    }

    private static <T> T coalesce(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
