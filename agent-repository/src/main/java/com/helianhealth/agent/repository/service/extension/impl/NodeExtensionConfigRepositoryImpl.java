package com.helianhealth.agent.repository.service.extension.impl;

import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.common.extension.support.DynamicSpiConfigSource;
import com.helianhealth.agent.common.extension.support.DynamicSpiDefinition;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.repository.service.NodeExtensionConfigQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 workflow 的节点扩展配置仓储实现
 * 负责将节点扩展配置领域模型转换为 extension 模块的领域模型
 */
@Service
@Slf4j
public class NodeExtensionConfigRepositoryImpl implements DynamicSpiConfigSource {

    @Autowired
    private NodeExtensionConfigQueryService extensionConfigQueryService;

    @Override
    public List<DynamicSpiDefinition> listEnabled() {
        try {
            // Fetch all enabled configurations
            List<NodeExtensionConfig> list = extensionConfigQueryService.selectByEnabled(1);
            return list.stream().map(this::convert).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch enabled configurations", e);
            throw new AgentException(AgentErrorCode.CONSOLE_OPERATION_FAILED, "Failed to fetch SPI extension configurations", e);
        }
    }

    @Override
    public DynamicSpiDefinition findBySpiInterfaceAndExtension(String spiInterface, String extensionName) {
        NodeExtensionConfig config = extensionConfigQueryService.selectBySpiInterfaceAndExtension(spiInterface, extensionName);
        return convert(config);
    }

    private DynamicSpiDefinition convert(NodeExtensionConfig source) {
        if (source == null) {
            return null;
        }
        DynamicSpiDefinition target = new DynamicSpiDefinition();
        // 只转换 SPI 相关字段，不包含数据库字段
        target.setSpiInterface(source.getSpiInterface());
        target.setExtensionName(source.getExtensionName());
        target.setClassName(source.getClassName());
        target.setSourceCode(source.getSourceCode());
        target.setDynamicCompile("ONLINE".equals(source.getExtensionMode()));
        return target;
    }

}
