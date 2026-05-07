package com.helianhealth.agent.console.model.convert;

import com.helianhealth.agent.console.controller.request.NodeExtensionConfigCreateReq;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigUpdateReq;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigDetailResponse;
import org.springframework.stereotype.Component;

/**
 * 节点扩展配置转换器
 */
@Component
public class NodeExtensionConfigConvert {

    /**
     * 创建请求 → Domain
     */
    public NodeExtensionConfig fromCreateReq(NodeExtensionConfigCreateReq request) {
        if (request == null) {
            return null;
        }
        return NodeExtensionConfig.builder()
                .bindingIds(request.getBindingIds())
                .spiInterface(request.getSpiInterface())
                .extensionName(request.getExtensionName())
                .className(request.getClassName())
                .sourceCode(request.getSourceCode())
                .description(request.getDescription())
                .extensionMode(request.getExtensionMode())
                .enabled(request.getEnabled())
                .extensionParamValue(request.getExtensionParamValue())
                .build();
    }

    /**
     * 更新请求 → Domain
     */
    public NodeExtensionConfig fromUpdateReq(NodeExtensionConfigUpdateReq request) {
        if (request == null) {
            return null;
        }
        return NodeExtensionConfig.builder()
                .configId(request.getConfigId())
                .bindingIds(request.getBindingIds())
                .spiInterface(request.getSpiInterface())
                .extensionName(request.getExtensionName())
                .className(request.getClassName())
                .sourceCode(request.getSourceCode())
                .description(request.getDescription())
                .extensionMode(request.getExtensionMode())
                .enabled(request.getEnabled())
                .extensionParamValue(request.getExtensionParamValue())
                .build();
    }

    /**
     * Domain 转换为详情响应对象
     */
    public NodeExtensionConfigDetailResponse doConvertToDetailResponse(NodeExtensionConfig config) {
        if (config == null) {
            return null;
        }

        NodeExtensionConfigDetailResponse response = new NodeExtensionConfigDetailResponse();
        response.setConfigId(config.getConfigId());
        response.setBindingIds(config.getBindingIds());
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
