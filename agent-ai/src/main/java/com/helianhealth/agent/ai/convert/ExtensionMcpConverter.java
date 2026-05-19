package com.helianhealth.agent.ai.convert;

import com.helianhealth.agent.ai.model.ExtensionMcpView;
import com.helianhealth.agent.ai.model.ExtensionRegisterMcpRequest;
import com.helianhealth.agent.ai.model.ExtensionUpdateMcpRequest;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.repository.model.dto.NodeExtensionConfigCreateReq;
import com.helianhealth.agent.repository.model.dto.NodeExtensionConfigUpdateReq;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP接口 - 扩展配置转换器
 */
public class ExtensionMcpConverter {

    public static ExtensionMcpView toMcpView(NodeExtensionConfig config) {
        if (config == null) return null;
        ExtensionMcpView view = new ExtensionMcpView();
        view.setConfigId(config.getConfigId());
        view.setBindingIds(config.getBindingIds());
        view.setSpiInterface(config.getSpiInterface());
        view.setExtensionName(config.getExtensionName());
        view.setClassName(config.getClassName());
        view.setSourceCode(config.getSourceCode());
        view.setDescription(config.getDescription());
        view.setExtensionMode(config.getExtensionMode());
        view.setEnabled(config.isEnabled() ? 1 : 0);
        view.setExtensionParamValue(config.getExtensionParamValue());
        return view;
    }

    public static List<ExtensionMcpView> toMcpViewList(List<NodeExtensionConfig> configs) {
        if (configs == null) return null;
        return configs.stream().map(ExtensionMcpConverter::toMcpView).collect(Collectors.toList());
    }

    public static NodeExtensionConfigCreateReq toCreateReq(ExtensionRegisterMcpRequest request) {
        if (request == null) return null;
        NodeExtensionConfigCreateReq req = new NodeExtensionConfigCreateReq();
        req.setBindingIds(request.getBindingIds());
        req.setExtensionName(request.getExtensionName());
        req.setClassName(request.getClassName());
        req.setSourceCode(request.getSourceCode());
        req.setDescription(request.getDescription());
        req.setExtensionMode(request.getExtensionMode());
        req.setEnabled(request.getEnabled());
        req.setExtensionParamValue(request.getExtensionParamValue());
        return req;
    }

    public static NodeExtensionConfigUpdateReq toUpdateReq(ExtensionUpdateMcpRequest request) {
        if (request == null) return null;
        NodeExtensionConfigUpdateReq req = new NodeExtensionConfigUpdateReq();
        req.setConfigId(request.getConfigId());
        req.setBindingIds(request.getBindingIds());
        req.setExtensionName(request.getExtensionName());
        req.setClassName(request.getClassName());
        req.setSourceCode(request.getSourceCode());
        req.setDescription(request.getDescription());
        req.setExtensionMode(request.getExtensionMode());
        req.setEnabled(request.getEnabled());
        req.setExtensionParamValue(request.getExtensionParamValue());
        return req;
    }
}
