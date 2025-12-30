package com.helianhealth.agent.workflow.service.extension;

import com.helianhealth.agent.extension.spi.dynamic.DynamicSpiManager;
import com.helianhealth.agent.extension.spi.model.DynamicSpiDefinition;
import com.helianhealth.agent.workflow.mapper.agent.InterfaceFlowNodeMapper;
import com.helianhealth.agent.workflow.mapper.agent.NodeExtensionConfigMapper;
import com.helianhealth.agent.workflow.model.domain.NodeExtensionConfigDO;
import com.helianhealth.agent.workflow.model.po.InterfaceWorkflowNodePO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 动态 SPI 管理服务（workflow 层）
 * 负责数据库 CRUD + SPI 运行时管理
 */
@Service
public class DynamicSpiAdminService {

    @Autowired
    private NodeExtensionConfigMapper extensionMapper;

    @Autowired
    private DynamicSpiManager dynamicSpiManager;

    @Autowired
    private InterfaceFlowNodeMapper nodeMapper;

    /**
     * New extension configuration and register it immediately when enabled
     */
    @Transactional(value = "workflowAgentTransactionManager", rollbackFor = Exception.class)
    public void createAndRegister(NodeExtensionConfigDO configDO) throws Exception {
        // Step 1: Insert extension config to database
        extensionMapper.insert(configDO);

        // Step 2: Update related node's remoteExtension field,this is the key for spi
        if (configDO.getNodeId() != null) {
            InterfaceWorkflowNodePO node = nodeMapper.selectByPrimaryKey(configDO.getNodeId());
            if (node != null) {
                node.setRemoteExtension(configDO.getExtensionName());
                nodeMapper.updateByPrimaryKey(node);
            }
        }

        // Step 3: Register to SPI runtime
        if (configDO.getEnabled() != null && configDO.getEnabled() == 1) {
            DynamicSpiDefinition definition = toDynamicSpi(configDO);
            dynamicSpiManager.loadAndRegister(definition);
        }
    }

    /**
     * Update configuration and hot refresh runtime implementation
     */
    @Transactional(value = "workflowAgentTransactionManager", rollbackFor = Exception.class)
    public void updateAndRefresh(NodeExtensionConfigDO configDO) throws Exception {
        // Step 1: Update database
        extensionMapper.update(configDO);

        // Step 2: Update related node's remoteExtension field (in same transaction)
        if (configDO.getNodeId() != null) {
            InterfaceWorkflowNodePO node = nodeMapper.selectByPrimaryKey(configDO.getNodeId());
            if (node != null) {
                node.setRemoteExtension(configDO.getExtensionName());
                nodeMapper.updateByPrimaryKey(node);
            }
        }

        // Step 3: Refresh SPI runtime (after transaction commit)
        DynamicSpiDefinition definition = toDynamicSpi(configDO);
        dynamicSpiManager.refresh(definition);
    }

    /**
     * 按 ID 刷新指定节点扩展
     */
    public void refresh(Integer codeId) throws Exception {
        NodeExtensionConfigDO configDO = extensionMapper.selectById(codeId);
        if (configDO == null) {
            throw new IllegalArgumentException("Extension config not found: " + codeId);
        }

        DynamicSpiDefinition definition = toDynamicSpi(configDO);
        dynamicSpiManager.refresh(definition);
    }

    /**
     * Disable and unregister runtime implementation
     */
    @Transactional(value = "workflowAgentTransactionManager", rollbackFor = Exception.class)
    public void disableAndUnregister(Integer codeId) {
        NodeExtensionConfigDO configDO = extensionMapper.selectById(codeId);
        if (configDO == null) {
            return;
        }

        configDO.setEnabled(0);
        extensionMapper.update(configDO);

        dynamicSpiManager.remove(configDO.getSpiInterface(), configDO.getExtensionName());
    }

    /**
     * List all enabled configurations from database
     */
    public List<NodeExtensionConfigDO> listEnabledConfigs() {
        return extensionMapper.selectByEnabled(1);
    }

    /**
     * 更新节点的 remoteExtension 字段
     */
    private void updateNodeRemoteExtension(NodeExtensionConfigDO configDO) {
        if (configDO.getNodeId() != null) {
            InterfaceWorkflowNodePO node = nodeMapper.selectByPrimaryKey(configDO.getNodeId());
            if (node != null) {
                node.setRemoteExtension(configDO.getExtensionName());
                nodeMapper.updateByPrimaryKey(node);
            }
        }
    }

    /**
     * DO 转 SPI 定义（只包含 SPI 相关字段）
     */
    private DynamicSpiDefinition toDynamicSpi(NodeExtensionConfigDO source) {
        DynamicSpiDefinition target = new DynamicSpiDefinition();
        target.setSpiInterface(source.getSpiInterface());
        target.setExtensionName(source.getExtensionName());
        target.setClassName(source.getClassName());
        target.setSourceCode(source.getSourceCode());
        return target;
    }
}
