package com.helianhealth.agent.console.model.convert;

import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigDetailResponse;
import org.springframework.stereotype.Repository;

/**
 * 节点扩展配置转换器
 */
@Repository
public class NodeExtensionConfigConvert {

    /**
     * Domain 转换为详情响应对象
     */
    public NodeExtensionConfigDetailResponse doConvertToDetailResponse(NodeExtensionConfig config) {
        if (config == null) {
            return null;
        }

        NodeExtensionConfigDetailResponse response = new NodeExtensionConfigDetailResponse();
        response.setCodeId(config.getCodeId());
        response.setNodeId(config.getNodeId());
        response.setSpiInterface(config.getSpiInterface());
        response.setExtensionName(config.getExtensionName());
        response.setClassName(config.getClassName());
        response.setSourceCode(config.getSourceCode());
        response.setDescription(config.getDescription());
        response.setExtensionMode(config.getExtensionMode());
        response.setEnabled(config.getEnabled());
        response.setExtensionParamValue(config.getExtensionParamValue());

        return response;
    }
}
