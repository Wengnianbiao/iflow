package com.helianhealth.agent.console.model.convert;

import com.helianhealth.agent.repository.model.po.NodeExtensionConfigDO;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigDetailResponse;
import org.springframework.stereotype.Repository;

/**
 * 节点扩展配置转换器
 */
@Repository
public class NodeExtensionConfigConvert {

    /**
     * DO 转换为详情响应对象
     */
    public NodeExtensionConfigDetailResponse doConvertToDetailResponse(NodeExtensionConfigDO configDO) {
        if (configDO == null) {
            return null;
        }

        NodeExtensionConfigDetailResponse response = new NodeExtensionConfigDetailResponse();
        response.setCodeId(configDO.getCodeId());
        response.setNodeId(configDO.getNodeId());
        response.setSpiInterface(configDO.getSpiInterface());
        response.setExtensionName(configDO.getExtensionName());
        response.setClassName(configDO.getClassName());
        response.setSourceCode(configDO.getSourceCode());
        response.setDescription(configDO.getDescription());
        response.setExtensionMode(configDO.getExtensionMode());
        response.setEnabled(configDO.getEnabled());
        response.setExtensionParamValue(configDO.getExtensionParamValue());

        return response;
    }
}
