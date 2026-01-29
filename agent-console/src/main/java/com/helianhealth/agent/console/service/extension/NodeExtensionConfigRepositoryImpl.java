package com.helianhealth.agent.console.service.extension;

import com.helianhealth.agent.common.extension.model.DynamicSpiDefinition;
import com.helianhealth.agent.common.extension.config.DynamicSpiConfigSource;
import com.helianhealth.agent.console.mapper.agent.NodeExtensionConfigMapper;
import com.helianhealth.agent.console.model.domain.NodeExtensionConfigDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 workflow 的节点扩展配置仓储实现
 * 负责将节点扩展配置 DO 转换为 extension 模块的领域模型
 */
@Service
@Slf4j
public class NodeExtensionConfigRepositoryImpl implements DynamicSpiConfigSource {

    @Autowired
    private NodeExtensionConfigMapper nodeExtensionConfigMapper;

    @Override
    public List<DynamicSpiDefinition> listEnabled() {
        try {
            // Fetch all enabled configurations
            List<NodeExtensionConfigDO> list = nodeExtensionConfigMapper.selectByEnabled(1);
            return list.stream().map(this::convert).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch enabled configurations", e);
            throw new RuntimeException("Failed to fetch SPI extension configurations", e);
        }
    }

    @Override
    public DynamicSpiDefinition findBySpiInterfaceAndExtension(String spiInterface, String extensionName) {
        NodeExtensionConfigDO doObj = nodeExtensionConfigMapper.selectBySpiInterfaceAndExtension(spiInterface, extensionName);
        return convert(doObj);
    }

    private DynamicSpiDefinition convert(NodeExtensionConfigDO source) {
        if (source == null) {
            return null;
        }
        DynamicSpiDefinition target = new DynamicSpiDefinition();
        // 只转换 SPI 相关字段，不包含数据库字段
        target.setSpiInterface(source.getSpiInterface());
        target.setExtensionName(source.getExtensionName());
        target.setClassName(source.getClassName());
        target.setSourceCode(source.getSourceCode());
        return target;
    }

}
