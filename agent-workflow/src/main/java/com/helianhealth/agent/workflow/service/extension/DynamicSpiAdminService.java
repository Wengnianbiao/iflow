package com.helianhealth.agent.workflow.service.extension;

import com.helianhealth.agent.common.extension.dynamic.DynamicSpiManager;
import com.helianhealth.agent.common.extension.model.DynamicSpiDefinition;
import com.helianhealth.agent.workflow.mapper.agent.InterfaceFlowNodeMapper;
import com.helianhealth.agent.workflow.mapper.agent.NodeExtensionConfigMapper;
import com.helianhealth.agent.workflow.model.domain.NodeExtensionConfigDO;
import com.helianhealth.agent.workflow.model.po.InterfaceWorkflowNodePO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Service
public class DynamicSpiAdminService {

    private static final Logger log = LoggerFactory.getLogger(DynamicSpiAdminService.class);

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
            // Step 1: Query old configuration to get old extensionName (before update)
            NodeExtensionConfigDO oldConfig = extensionMapper.selectById(configDO.getCodeId());
            String oldExtensionName = oldConfig != null ? oldConfig.getExtensionName() : null;
            String oldSpiInterface = oldConfig != null ? oldConfig.getSpiInterface() : null;

            // Step 2: Update database
            extensionMapper.update(configDO);

            // Step 3: Update related node's remoteExtension field (in same transaction)
            if (configDO.getNodeId() != null) {
                InterfaceWorkflowNodePO node = nodeMapper.selectByPrimaryKey(configDO.getNodeId());
                if (node != null) {
                    node.setRemoteExtension(configDO.getExtensionName());
                    nodeMapper.updateByPrimaryKey(node);
                }
            }

            // Step 4: Unload old SPI and load new one after transaction commit
            DynamicSpiDefinition newDefinition = toDynamicSpi(configDO);
            updateSpiAfterCommit(oldSpiInterface, oldExtensionName, newDefinition);

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
                }
            }
        });
    }

    /**
     * Update SPI after transaction commit: unload old and load new
     * This method handles extensionName change correctly
     */
    private void updateSpiAfterCommit(String oldSpiInterface, String oldExtensionName, DynamicSpiDefinition newDefinition) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // Step 1: Remove old SPI if exists
                    if (oldSpiInterface != null && oldExtensionName != null) {
                        dynamicSpiManager.remove(oldSpiInterface, oldExtensionName);
                        log.debug("Removed old SPI: interface={}, extension={}",
                                oldSpiInterface, oldExtensionName);
                    }

                    // Step 2: Register new SPI
                    dynamicSpiManager.loadAndRegister(newDefinition);
                    log.info("Successfully updated dynamic SPI after commit: interface={}, extension={}",
                            newDefinition.getSpiInterface(), newDefinition.getExtensionName());
                } catch (Exception e) {
                    log.error("Failed to update dynamic SPI after commit: old=[{}#{}], new=[{}#{}]",
                            oldSpiInterface, oldExtensionName,
                            newDefinition.getSpiInterface(), newDefinition.getExtensionName(), e);
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
