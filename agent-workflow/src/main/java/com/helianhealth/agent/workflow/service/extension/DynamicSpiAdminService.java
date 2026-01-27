package com.helianhealth.agent.workflow.service.extension;

import com.alibaba.fastjson2.JSON;
import com.helianhealth.agent.common.base.ExtensionConstants;
import com.helianhealth.agent.common.extension.dynamic.DynamicSpiManager;
import com.helianhealth.agent.common.extension.model.DynamicSpiDefinition;
import com.helianhealth.agent.common.utils.JsonUtils;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamic SPI Admin Service (workflow layer)
 * Responsible for database CRUD + SPI runtime management
 */
@Service
public class DynamicSpiAdminService {

    private static final Logger log = LoggerFactory.getLogger(DynamicSpiAdminService.class);

    // SPI 接口类型常量
    private static final String SPI_INTERFACE_REMOTE_INVOKER = "com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker";
    private static final String SPI_INTERFACE_AGENT_FILTER = "com.helianhealth.agent.invocation.remote.filter.AgentFilter";

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

            // Step 2: Update related nodes based on SPI interface type
            String spiInterface = configDO.getSpiInterface();
            if (configDO.getNodeId() != null && !configDO.getNodeId().isEmpty()) {
                List<Integer> nodeIds = configDO.getNodeId();
                if (SPI_INTERFACE_REMOTE_INVOKER.equals(spiInterface)) {
                    // RemoteInvoker: 单节点绑定，更新 remote_extension 字段
                    Integer nodeId = nodeIds.get(0); // 只取第一个
                    updateNodeRemoteExtension(nodeId, configDO.getExtensionName());
                } else if (SPI_INTERFACE_AGENT_FILTER.equals(spiInterface)) {
                    // Filter: 多节点绑定，更新 extension_param 字段
                    for (Integer nodeId : nodeIds) {
                        updateNodeExtensionParam(nodeId, configDO.getExtensionName());
                    }
                }
            }

            // Step 3: Register to SPI runtime after transaction commit
            if (configDO.getEnabled() != null && configDO.getEnabled() == 1) {
                DynamicSpiDefinition definition = toDynamicSpi(configDO);
                registerAfterCommit(definition);
            }

            log.info("Successfully created extension config: spiInterface={}, extensionName={}, nodeIds={}",
                    configDO.getSpiInterface(), configDO.getExtensionName(), configDO.getNodeId());
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
            List<Integer> oldNodeIds = oldConfig != null ? oldConfig.getNodeId() : null;

            // Step 2: Update database
            extensionMapper.update(configDO);

            // Step 3: Update related nodes based on SPI interface type
            String spiInterface = configDO.getSpiInterface();
            
            // 3.1 清理旧节点的配置
            if (oldNodeIds != null && !oldNodeIds.isEmpty() && oldSpiInterface != null) {
                if (SPI_INTERFACE_REMOTE_INVOKER.equals(oldSpiInterface)) {
                    // 清理旧节点的 remote_extension
                    for (Integer oldNodeId : oldNodeIds) {
                        clearNodeRemoteExtension(oldNodeId);
                    }
                } else if (SPI_INTERFACE_AGENT_FILTER.equals(oldSpiInterface)) {
                    // 清理旧节点的 extension_param
                    if (oldExtensionName != null) {
                        for (Integer oldNodeId : oldNodeIds) {
                            clearNodeExtensionParam(oldNodeId, oldExtensionName);
                        }
                    }
                }
            }
            
            // 3.2 更新新节点的配置
            if (configDO.getNodeId() != null && !configDO.getNodeId().isEmpty()) {
                List<Integer> nodeIds = configDO.getNodeId();
                if (SPI_INTERFACE_REMOTE_INVOKER.equals(spiInterface)) {
                    // RemoteInvoker: 单节点绑定，更新 remote_extension 字段
                    Integer nodeId = nodeIds.get(0); // 只取第一个
                    updateNodeRemoteExtension(nodeId, configDO.getExtensionName());
                } else if (SPI_INTERFACE_AGENT_FILTER.equals(spiInterface)) {
                    // Filter: 多节点绑定，更新 extension_param 字段
                    for (Integer nodeId : nodeIds) {
                        updateNodeExtensionParam(nodeId, configDO.getExtensionName());
                    }
                }
            }

            // Step 4: Unload old SPI and load new one after transaction commit
            DynamicSpiDefinition newDefinition = toDynamicSpi(configDO);
            updateSpiAfterCommit(oldSpiInterface, oldExtensionName, newDefinition);

            log.info("Successfully updated extension config: spiInterface={}, extensionName={}, nodeIds={}",
                    configDO.getSpiInterface(), configDO.getExtensionName(), configDO.getNodeId());
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
     * 根据 SPI 接口类型查询扩展配置列表
     */
    public List<NodeExtensionConfigDO> listExtensionsBySpiInterface(String spiInterface) {
        return extensionMapper.selectBySpiInterface(spiInterface);
    }

    /**
     * 创建接口扩展（InterfaceRemoteInvoker）
     * - 单节点绑定
     * - 更新节点的 remote_extension 字段
     */
    @Transactional(value = "workflowAgentTransactionManager", rollbackFor = Exception.class)
    public void createInterfaceExtension(NodeExtensionConfigDO configDO) {
        try {
            // Step 1: Insert extension config to database
            extensionMapper.insert(configDO);

            // Step 2: Update node's remote_extension field
            if (configDO.getNodeId() != null && !configDO.getNodeId().isEmpty()) {
                Integer nodeId = configDO.getNodeId().get(0); // 取第一个（单节点）
                updateNodeRemoteExtension(nodeId, configDO.getExtensionName());
            }

            // Step 3: Register to SPI runtime after transaction commit
            if (configDO.getEnabled() != null && configDO.getEnabled() == 1) {
                DynamicSpiDefinition definition = toDynamicSpi(configDO);
                registerAfterCommit(definition);
            }

            log.info("Successfully created interface extension: extensionName={}, nodeId={}",
                    configDO.getExtensionName(), configDO.getNodeId());
        } catch (Exception e) {
            log.error("Failed to create interface extension: extensionName={}, className={}",
                    configDO.getExtensionName(), configDO.getClassName(), e);
            throw e;
        }
    }

    /**
     * 更新接口扩展（InterfaceRemoteInvoker）
     */
    @Transactional(value = "workflowAgentTransactionManager", rollbackFor = Exception.class)
    public void updateInterfaceExtension(NodeExtensionConfigDO configDO) {
        try {
            // Step 1: Query old configuration
            NodeExtensionConfigDO oldConfig = extensionMapper.selectById(configDO.getCodeId());
            String oldExtensionName = oldConfig != null ? oldConfig.getExtensionName() : null;
            String oldSpiInterface = oldConfig != null ? oldConfig.getSpiInterface() : null;
            List<Integer> oldNodeIds = oldConfig != null ? oldConfig.getNodeId() : null;

            // Step 2: Update database
            extensionMapper.update(configDO);

            // Step 3: Clear old node's remote_extension
            if (oldNodeIds != null && !oldNodeIds.isEmpty()) {
                for (Integer oldNodeId : oldNodeIds) {
                    clearNodeRemoteExtension(oldNodeId);
                }
            }

            // Step 4: Update new node's remote_extension
            if (configDO.getNodeId() != null && !configDO.getNodeId().isEmpty()) {
                Integer nodeId = configDO.getNodeId().get(0); // 取第一个（单节点）
                updateNodeRemoteExtension(nodeId, configDO.getExtensionName());
            }

            // Step 5: Unload old SPI and load new one after transaction commit
            DynamicSpiDefinition newDefinition = toDynamicSpi(configDO);
            updateSpiAfterCommit(oldSpiInterface, oldExtensionName, newDefinition);

            log.info("Successfully updated interface extension: extensionName={}, nodeId={}",
                    configDO.getExtensionName(), configDO.getNodeId());
        } catch (Exception e) {
            log.error("Failed to update interface extension: extensionName={}, className={}",
                    configDO.getExtensionName(), configDO.getClassName(), e);
            throw e;
        }
    }

    /**
     * 创建过滤器扩展（AgentFilter）
     * - 多节点绑定
     * - 更新节点的 extension_param 字段
     */
    @Transactional(value = "workflowAgentTransactionManager", rollbackFor = Exception.class)
    public void createFilterExtension(NodeExtensionConfigDO configDO) {
        try {
            // Step 1: Insert extension config to database
            extensionMapper.insert(configDO);

            // Step 2: Update nodes' extension_param field
            if (configDO.getNodeId() != null && !configDO.getNodeId().isEmpty()) {
                for (Integer nodeId : configDO.getNodeId()) {
                    updateNodeExtensionParam(nodeId, configDO.getExtensionName());
                }
            }

            // Step 3: Register to SPI runtime after transaction commit
            if (configDO.getEnabled() != null && configDO.getEnabled() == 1) {
                DynamicSpiDefinition definition = toDynamicSpi(configDO);
                registerAfterCommit(definition);
            }

            log.info("Successfully created filter extension: extensionName={}, nodeIds={}",
                    configDO.getExtensionName(), configDO.getNodeId());
        } catch (Exception e) {
            log.error("Failed to create filter extension: extensionName={}, className={}",
                    configDO.getExtensionName(), configDO.getClassName(), e);
            throw e;
        }
    }

    /**
     * 更新过滤器扩展（AgentFilter）
     */
    @Transactional(value = "workflowAgentTransactionManager", rollbackFor = Exception.class)
    public void updateFilterExtension(NodeExtensionConfigDO configDO) {
        try {
            // Step 1: Query old configuration
            NodeExtensionConfigDO oldConfig = extensionMapper.selectById(configDO.getCodeId());
            String oldExtensionName = oldConfig != null ? oldConfig.getExtensionName() : null;
            String oldSpiInterface = oldConfig != null ? oldConfig.getSpiInterface() : null;
            List<Integer> oldNodeIds = oldConfig != null ? oldConfig.getNodeId() : null;

            // Step 2: Update database
            extensionMapper.update(configDO);

            // Step 3: Clear old nodes' extension_param
            if (oldNodeIds != null && !oldNodeIds.isEmpty() && oldExtensionName != null) {
                for (Integer oldNodeId : oldNodeIds) {
                    clearNodeExtensionParam(oldNodeId, oldExtensionName);
                }
            }

            // Step 4: Update new nodes' extension_param
            if (configDO.getNodeId() != null && !configDO.getNodeId().isEmpty()) {
                for (Integer nodeId : configDO.getNodeId()) {
                    updateNodeExtensionParam(nodeId, configDO.getExtensionName());
                }
            }

            // Step 5: Unload old SPI and load new one after transaction commit
            DynamicSpiDefinition newDefinition = toDynamicSpi(configDO);
            updateSpiAfterCommit(oldSpiInterface, oldExtensionName, newDefinition);

            log.info("Successfully updated filter extension: extensionName={}, nodeIds={}",
                    configDO.getExtensionName(), configDO.getNodeId());
        } catch (Exception e) {
            log.error("Failed to update filter extension: extensionName={}, className={}",
                    configDO.getExtensionName(), configDO.getClassName(), e);
            throw e;
        }
    }

    /**
     * 获取系统支持的所有SPI接口列表
     * 返回当前系统可扩展的SPI接口全限定名
     */
    public List<String> getSupportedSpiInterfaces() {
        return java.util.Arrays.asList(
            "com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker",
            "com.helianhealth.agent.invocation.remote.filter.AgentFilter"
        );
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

    /**
     * 更新节点的 remote_extension 字段（用于 RemoteInvoker 扩展）
     */
    private void updateNodeRemoteExtension(Integer nodeId, String extensionName) {
        InterfaceWorkflowNodePO node = nodeMapper.selectByPrimaryKey(nodeId);
        if (node != null) {
            node.setRemoteExtension(extensionName);
            nodeMapper.updateByPrimaryKey(node);
            log.info("Updated node {} remote_extension to: {}", nodeId, extensionName);
        } else {
            log.warn("Node not found for update: {}", nodeId);
        }
    }

    /**
     * 更新节点的 extension_param 字段（用于 Filter 扩展）
     * 将 filter 的 extensionName 作为 key，值为 "true"
     */
    private void updateNodeExtensionParam(Integer nodeId, String extensionName) {
        InterfaceWorkflowNodePO node = nodeMapper.selectByPrimaryKey(nodeId);
        if (node != null) {
            // 解析现有的 extension_param
            Map<String, String> extensionParam = new HashMap<>();
            String existingParam = node.getExtensionParam();
            if (existingParam != null && !existingParam.trim().isEmpty()) {
                try {
                    extensionParam = JsonUtils.fromJsonStringToMap(existingParam);
                } catch (Exception e) {
                    log.warn("Failed to parse existing extension_param for node {}: {}", nodeId, existingParam, e);
                }
            }

            // 添加新的 filter 配置（key: "filter.{extensionName}", value: "true"）
            String filterKey = "filter." + extensionName;
            extensionParam.put(filterKey, "true");

            // 回写到节点
            node.setExtensionParam(JsonUtils.toJsonString(extensionParam));
            nodeMapper.updateByPrimaryKey(node);
            log.info("Updated node {} extension_param, added filter: {}", nodeId, filterKey);
        } else {
            log.warn("Node not found for update: {}", nodeId);
        }
    }

    /**
     * 清理节点的 remote_extension 字段（用于更新时清理旧配置）
     */
    private void clearNodeRemoteExtension(Integer nodeId) {
        InterfaceWorkflowNodePO node = nodeMapper.selectByPrimaryKey(nodeId);
        if (node != null) {
            node.setRemoteExtension(null);
            nodeMapper.updateByPrimaryKey(node);
            log.info("Cleared node {} remote_extension", nodeId);
        }
    }

    /**
     * 清理节点的 extension_param 字段中的特定 filter（用于更新时清理旧配置）
     */
    private void clearNodeExtensionParam(Integer nodeId, String extensionName) {
        InterfaceWorkflowNodePO node = nodeMapper.selectByPrimaryKey(nodeId);
        if (node != null) {
            String existingParam = node.getExtensionParam();
            if (existingParam != null && !existingParam.trim().isEmpty()) {
                try {
                    Map<String, String> extensionParam = JsonUtils.fromJsonStringToMap(existingParam);
                    String filterKey = "filter." + extensionName;
                    
                    if (extensionParam.remove(filterKey) != null) {
                        // 如果成功移除了该 filter，回写到节点
                        node.setExtensionParam(JsonUtils.toJsonString(extensionParam));
                        nodeMapper.updateByPrimaryKey(node);
                        log.info("Cleared node {} extension_param filter: {}", nodeId, filterKey);
                    }
                } catch (Exception e) {
                    log.warn("Failed to clear extension_param for node {}: {}", nodeId, existingParam, e);
                }
            }
        }
    }
}
