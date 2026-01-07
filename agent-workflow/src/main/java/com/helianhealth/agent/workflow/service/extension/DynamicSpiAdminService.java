package com.helianhealth.agent.workflow.service.extension;

import com.helianhealth.agent.extension.spi.dynamic.DynamicSpiManager;
import com.helianhealth.agent.extension.spi.model.DynamicSpiDefinition;
import com.helianhealth.agent.workflow.mapper.agent.InterfaceFlowNodeMapper;
import com.helianhealth.agent.workflow.mapper.agent.NodeExtensionConfigMapper;
import com.helianhealth.agent.workflow.model.domain.NodeExtensionConfigDO;
import com.helianhealth.agent.workflow.model.po.InterfaceWorkflowNodePO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Dynamic SPI Admin Service (workflow layer)
 * Responsible for database CRUD + SPI runtime management
 */
@Slf4j
@Service
public class DynamicSpiAdminService {

    @Autowired
    private NodeExtensionConfigMapper extensionMapper;

    @Autowired
    private DynamicSpiManager dynamicSpiManager;

    @Autowired
    private InterfaceFlowNodeMapper nodeMapper;

    /**
     * Create new extension configuration and register it immediately when enabled
     * Database operations are wrapped with exception handling
     */
    @Transactional(value = "workflowAgentTransactionManager", rollbackFor = Exception.class)
    public void createAndRegister(NodeExtensionConfigDO configDO) {
        try {
            // Step 1: Insert extension config to database
            extensionMapper.insert(configDO);

            // Step 2: Update related node's remoteExtension field
            if (configDO.getNodeId() != null) {
                InterfaceWorkflowNodePO node = nodeMapper.selectByPrimaryKey(configDO.getNodeId());
                if (node != null) {
                    node.setRemoteExtension(configDO.getExtensionName());
                    nodeMapper.updateByPrimaryKey(node);
                }
            }

            // Step 3: Register to SPI runtime after transaction commit
            if (configDO.getEnabled() != null && configDO.getEnabled() == 1) {
                DynamicSpiDefinition definition = toDynamicSpi(configDO);
                registerAfterCommit(definition);
            }

            log.info("Successfully created extension config: extensionName={}, className={}",
                    configDO.getExtensionName(), configDO.getClassName());
        } catch (Exception e) {
            log.error("Failed to create extension config: extensionName={}, className={}",
                    configDO.getExtensionName(), configDO.getClassName(), e);
            throw e;
        }
    }

    /**
     * Update configuration and hot refresh runtime implementation
     * Database operations are wrapped with exception handling
     */
    @Transactional(value = "workflowAgentTransactionManager", rollbackFor = Exception.class)
    public void updateAndRefresh(NodeExtensionConfigDO configDO) {
        try {
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

            // Step 3: Refresh SPI runtime after transaction commit
            DynamicSpiDefinition definition = toDynamicSpi(configDO);
            refreshAfterCommit(definition);

            log.info("Successfully updated extension config: extensionName={}, className={}",
                    configDO.getExtensionName(), configDO.getClassName());
        } catch (Exception e) {
            log.error("Failed to update extension config: extensionName={}, className={}",
                    configDO.getExtensionName(), configDO.getClassName(), e);
            throw e;
        }
    }

    /**
     * Refresh specified node extension by ID
     * Both database and SPI operations are wrapped with exception handling
     */
    public void refresh(Integer codeId) throws Exception {
        try {
            NodeExtensionConfigDO configDO = extensionMapper.selectById(codeId);
            if (configDO == null) {
                throw new IllegalArgumentException("Extension config not found: " + codeId);
            }

            DynamicSpiDefinition definition = toDynamicSpi(configDO);
            dynamicSpiManager.refresh(definition);

            log.info("Successfully refreshed dynamic SPI: interface={}, extension={}",
                    definition.getSpiInterface(), definition.getExtensionName());
        } catch (Exception e) {
            log.error("Failed to refresh dynamic SPI for codeId: {}", codeId, e);
            throw e;
        }
    }

    /**
     * Disable and unregister runtime implementation
     * Database operations are wrapped with exception handling
     */
    @Transactional(value = "workflowAgentTransactionManager", rollbackFor = Exception.class)
    public void disableAndUnregister(Integer codeId) {
        try {
            NodeExtensionConfigDO configDO = extensionMapper.selectById(codeId);
            if (configDO == null) {
                log.warn("Extension config not found for disable: {}", codeId);
                return;
            }

            // Update database
            configDO.setEnabled(0);
            extensionMapper.update(configDO);

            // Unregister from SPI runtime after transaction commit
            String spiInterface = configDO.getSpiInterface();
            String extensionName = configDO.getExtensionName();
            unregisterAfterCommit(spiInterface, extensionName);

            log.info("Successfully disabled extension config: codeId={}, extensionName={}",
                    codeId, extensionName);
        } catch (Exception e) {
            log.error("Failed to disable extension config: codeId={}", codeId, e);
            throw e;
        }
    }

    /**
     * List all enabled configurations from database
     */
    public List<NodeExtensionConfigDO> listEnabledConfigs() {
        return extensionMapper.selectByEnabled(1);
    }

    /**
     * Register SPI after transaction commit to ensure data consistency
     */
    private void registerAfterCommit(DynamicSpiDefinition definition) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    dynamicSpiManager.loadAndRegister(definition);
                    log.info("Successfully registered dynamic SPI after commit: interface={}, extension={}",
                            definition.getSpiInterface(), definition.getExtensionName());
                } catch (Exception e) {
                    log.error("Failed to register dynamic SPI after commit: interface={}, extension={}, className={}",
                            definition.getSpiInterface(), definition.getExtensionName(),
                            definition.getClassName(), e);
                    // Note: Transaction already committed, cannot rollback
                    // Manual compensation may be required
                }
            }
        });
    }

    /**
     * Refresh SPI after transaction commit to ensure data consistency
     */
    private void refreshAfterCommit(DynamicSpiDefinition definition) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    dynamicSpiManager.refresh(definition);
                    log.info("Successfully refreshed dynamic SPI after commit: interface={}, extension={}",
                            definition.getSpiInterface(), definition.getExtensionName());
                } catch (Exception e) {
                    log.error("Failed to refresh dynamic SPI after commit: interface={}, extension={}, className={}",
                            definition.getSpiInterface(), definition.getExtensionName(),
                            definition.getClassName(), e);
                }
            }
        });
    }

    /**
     * Unregister SPI after transaction commit to ensure data consistency
     */
    private void unregisterAfterCommit(String spiInterface, String extensionName) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    dynamicSpiManager.remove(spiInterface, extensionName);
                    log.info("Successfully unregistered dynamic SPI after commit: interface={}, extension={}",
                            spiInterface, extensionName);
                } catch (Exception e) {
                    log.error("Failed to unregister dynamic SPI after commit: interface={}, extension={}",
                            spiInterface, extensionName, e);
                }
            }
        });
    }

    /**
     * Convert DO to SPI definition (only SPI related fields)
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
