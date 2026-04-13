package com.helianhealth.agent.repository.model.assembler;

import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.repository.model.po.NodeExtensionConfigPO;

/**
 * 节点扩展配置 PO/Domain 转换器
 */
public final class NodeExtensionConfigAssembler {

    private NodeExtensionConfigAssembler() {
    }

    public static NodeExtensionConfig toDomain(NodeExtensionConfigPO po) {
        if (po == null) {
            return null;
        }
        return NodeExtensionConfig.builder()
                .codeId(po.getCodeId())
                .nodeId(po.getNodeId())
                .spiInterface(po.getSpiInterface())
                .extensionName(po.getExtensionName())
                .className(po.getClassName())
                .sourceCode(po.getSourceCode())
                .description(po.getDescription())
                .extensionMode(po.getExtensionMode())
                .enabled(po.getEnabled())
                .extensionParamValue(po.getExtensionParamValue())
                .build();
    }

    public static NodeExtensionConfigPO toPO(NodeExtensionConfig config) {
        if (config == null) {
            return null;
        }
        NodeExtensionConfigPO po = new NodeExtensionConfigPO();
        po.setCodeId(config.getCodeId());
        po.setNodeId(config.getNodeId());
        po.setSpiInterface(config.getSpiInterface());
        po.setExtensionName(config.getExtensionName());
        po.setClassName(config.getClassName());
        po.setSourceCode(config.getSourceCode());
        po.setDescription(config.getDescription());
        po.setExtensionMode(config.getExtensionMode());
        po.setEnabled(config.getEnabled());
        po.setExtensionParamValue(config.getExtensionParamValue());
        return po;
    }
}
