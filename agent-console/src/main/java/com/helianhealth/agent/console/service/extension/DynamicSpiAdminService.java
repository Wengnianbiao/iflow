package com.helianhealth.agent.console.service.extension;

import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.common.extension.model.DynamicSpiDefinition;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.repository.mapper.agent.InterfaceFlowNodeMapper;
import com.helianhealth.agent.repository.mapper.agent.NodeExtensionConfigMapper;
import com.helianhealth.agent.console.model.convert.NodeExtensionConfigConvert;
import com.helianhealth.agent.repository.model.po.NodeExtensionConfigDO;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigCreateReq;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigDetailResponse;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigUpdateReq;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowNodePO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

/**
 * Dynamic SPI Admin Service
 *
 * 职责：
 * - 扩展注册/更新的完整业务流程（节点字段更新 + 配置持久化 + SPI注册）
 * - 扩展配置的数据库CRUD操作
 * - 通过 AgentExtensionLoader 进行SPI运行时注册/卸载管理
 */
@Service
public class DynamicSpiAdminService {

    private static final Logger log = LoggerFactory.getLogger(DynamicSpiAdminService.class);

    @Autowired
    private NodeExtensionConfigMapper extensionMapper;

    @Autowired
    private ExtensionNodeUpdateService extensionNodeUpdateService;

    @Autowired
    private InterfaceFlowNodeMapper nodeMapper;

    @Autowired
    private NodeExtensionConfigConvert extensionConfigConvert;

    /**
     * 注册扩展（完整流程：更新节点字段 + 持久化 + 注册SPI）
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void registerExtension(ExtensionType type, NodeExtensionConfigCreateReq request) {
        try {
            // 转换Request为DO
            NodeExtensionConfigDO configDO = convertCreateReqToDO(request);

            // Step 1: 更新节点字段（传递extensionParamValue）
            extensionNodeUpdateService.updateNodeFields(
                type,
                configDO.getExtensionName(),
                configDO.getNodeId(),
                request.getExtensionParamValue()
            );

            // Step 2: 插入扩展配置到数据库
            extensionMapper.insert(configDO);

            // Step 3: 如果启用，事务提交后注册到SPI运行时
            if (configDO.getEnabled() != null && configDO.getEnabled() == 1) {
                DynamicSpiDefinition definition = toDynamicSpi(configDO);
                registerAfterCommit(definition);
            }

            log.info("Successfully registered extension: type={}, extensionName={}, nodeIds={}, paramValue={}",
                    type.getDescription(), configDO.getExtensionName(), configDO.getNodeId(), request.getExtensionParamValue());
        } catch (Exception e) {
            log.error("Failed to register extension: extensionName={}, className={}",
                    request.getExtensionName(), request.getClassName(), e);
            throw e;
        }
    }

    /**
     * 更新扩展（完整流程：清理旧节点 + 更新新节点 + 持久化 + 刷新SPI）
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void updateExtension(ExtensionType type, NodeExtensionConfigUpdateReq request) {
        try {
            // 转换Request为DO
            NodeExtensionConfigDO configDO = convertUpdateReqToDO(request);

            // Step 1: 查询旧配置,清理旧节点字段
            NodeExtensionConfigDO oldConfig = extensionMapper.selectById(configDO.getCodeId());
            if (oldConfig != null && oldConfig.getNodeId() != null) {
                extensionNodeUpdateService.clearNodeFields(
                    type,
                    oldConfig.getExtensionName(),
                    oldConfig.getNodeId()
                );
            }

            // Step 2: 更新新节点字段（传递extensionParamValue）
            extensionNodeUpdateService.updateNodeFields(
                type,
                configDO.getExtensionName(),
                configDO.getNodeId(),
                request.getExtensionParamValue()
            );

            // Step 3: 更新数据库
            extensionMapper.update(configDO);

            // Step 4: 卸载旧SPI并加载新SPI（事务提交后）
            String oldSpiInterface = oldConfig != null ? oldConfig.getSpiInterface() : null;
            String oldExtensionName = oldConfig != null ? oldConfig.getExtensionName() : null;
            DynamicSpiDefinition newDefinition = toDynamicSpi(configDO);
            updateSpiAfterCommit(oldSpiInterface, oldExtensionName, newDefinition);

            log.info("Successfully updated extension: type={}, extensionName={}, nodeIds={}, paramValue={}",
                    type.getDescription(), configDO.getExtensionName(), configDO.getNodeId(), request.getExtensionParamValue());
        } catch (Exception e) {
            log.error("Failed to update extension: extensionName={}, className={}",
                    request.getExtensionName(), request.getClassName(), e);
            throw e;
        }
    }

    /**
     * 保存扩展配置并注册SPI（不包含节点字段更新）
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void saveAndRegisterExtension(NodeExtensionConfigDO configDO) {
        try {
            // Step 1: 插入扩展配置到数据库
            extensionMapper.insert(configDO);

            // Step 2: 如果启用，事务提交后注册到SPI运行时
            if (configDO.getEnabled() != null && configDO.getEnabled() == 1) {
                DynamicSpiDefinition definition = toDynamicSpi(configDO);
                registerAfterCommit(definition);
            }

            log.info("Successfully saved extension: extensionName={}, nodeIds={}",
                    configDO.getExtensionName(), configDO.getNodeId());
        } catch (Exception e) {
            log.error("Failed to save extension: extensionName={}, className={}",
                    configDO.getExtensionName(), configDO.getClassName(), e);
            throw e;
        }
    }

    /**
     * 更新扩展配置并刷新SPI
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void updateAndRefreshExtension(NodeExtensionConfigDO oldConfig, NodeExtensionConfigDO newConfig) {
        try {
            // Step 1: 更新数据库
            extensionMapper.update(newConfig);

            // Step 2: 卸载旧SPI并加载新SPI（事务提交后）
            String oldSpiInterface = oldConfig != null ? oldConfig.getSpiInterface() : null;
            String oldExtensionName = oldConfig != null ? oldConfig.getExtensionName() : null;
            DynamicSpiDefinition newDefinition = toDynamicSpi(newConfig);
            updateSpiAfterCommit(oldSpiInterface, oldExtensionName, newDefinition);

            log.info("Successfully updated extension: extensionName={}, nodeIds={}",
                    newConfig.getExtensionName(), newConfig.getNodeId());
        } catch (Exception e) {
            log.error("Failed to update extension: extensionName={}, className={}",
                    newConfig.getExtensionName(), newConfig.getClassName(), e);
            throw e;
        }
    }

    /**
     * 根据ID查询扩展配置
     */
    public NodeExtensionConfigDO getExtensionById(Integer codeId) {
        return extensionMapper.selectById(codeId);
    }

    /**
     * 查询扩展配置详情（带节点参数值）
     * 从关联节点的 extension_param 中提取该 filter 的实际配置值
     *
     * @param codeId 扩展配置ID
     * @return 扩展配置详情响应对象
     */
    public NodeExtensionConfigDetailResponse getExtensionDetailById(Integer codeId) {
        NodeExtensionConfigDO configDO = extensionMapper.selectById(codeId);
        if (configDO == null) {
            return null;
        }

        // 只有 Filter 类型才需要从节点提取参数
        if ("com.helianhealth.agent.invocation.remote.filter.AgentFilter".equals(configDO.getSpiInterface())) {
            List<Integer> nodeIds = configDO.getNodeId();
            if (nodeIds != null && !nodeIds.isEmpty()) {
                // 从第一个节点获取 extension_param
                Integer firstNodeId = nodeIds.get(0);
                InterfaceWorkflowNodePO node = nodeMapper.selectByPrimaryKey(firstNodeId);
                if (node != null && node.getExtensionParam() != null) {
                    try {
                        Map<String, String> extensionParam = JsonUtils.fromJsonStringToMap(node.getExtensionParam());
                        String filterKey = "filter." + configDO.getExtensionName();
                        String paramValue = extensionParam.get(filterKey);
                        // 设置到 DO 对象中（用于转换）
                        configDO.setExtensionParamValue(paramValue);
                    } catch (Exception e) {
                        log.warn("Failed to extract extensionParamValue for filter: {}", configDO.getExtensionName(), e);
                    }
                }
            }
        }

        // 转换为 Response 对象
        return extensionConfigConvert.doConvertToDetailResponse(configDO);
    }

    /**
     * Refresh specified node extension by ID
     */
    public void refresh(Integer codeId) throws Exception {
        try {
            NodeExtensionConfigDO configDO = extensionMapper.selectById(codeId);
            if (configDO == null) {
                throw new IllegalArgumentException("Extension config not found: " + codeId);
            }

            // 通过AgentExtensionLoader刷新扩展
            Class<?> interfaceClass = Class.forName(configDO.getSpiInterface());
            AgentExtensionLoader<?> loader = AgentExtensionLoader.getExtensionLoader(interfaceClass);
            loader.refreshDynamicExtension(
                    configDO.getExtensionName(),
                    configDO.getClassName(),
                    configDO.getSourceCode()
            );

            log.info("Successfully refreshed dynamic SPI: interface={}, extension={}",
                    configDO.getSpiInterface(), configDO.getExtensionName());
        } catch (Exception e) {
            log.error("Failed to refresh dynamic SPI for codeId: {}", codeId, e);
            throw e;
        }
    }

    /**
     * Disable and unregister runtime implementation
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
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
     * 获取系统支持的所有SPI接口列表
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
                    // 通过AgentExtensionLoader注册扩展
                    Class<?> interfaceClass = Class.forName(definition.getSpiInterface());
                    @SuppressWarnings("unchecked")
                    AgentExtensionLoader<Object> loader = (AgentExtensionLoader<Object>)
                            AgentExtensionLoader.getExtensionLoader(interfaceClass);

                    loader.registerDynamicExtension(
                            definition.getExtensionName(),
                            definition.getClassName(),
                            definition.getSourceCode()
                    );

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
     */
    private void updateSpiAfterCommit(String oldSpiInterface, String oldExtensionName, DynamicSpiDefinition newDefinition) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // Step 1: Remove old SPI if exists
                    if (oldSpiInterface != null && oldExtensionName != null) {
                        Class<?> oldInterfaceClass = Class.forName(oldSpiInterface);
                        @SuppressWarnings("unchecked")
                        AgentExtensionLoader<Object> oldLoader = (AgentExtensionLoader<Object>)
                                AgentExtensionLoader.getExtensionLoader(oldInterfaceClass);
                        oldLoader.removeDynamicExtension(oldExtensionName);

                        log.debug("Removed old SPI: interface={}, extension={}",
                                oldSpiInterface, oldExtensionName);
                    }

                    // Step 2: Register new SPI
                    Class<?> newInterfaceClass = Class.forName(newDefinition.getSpiInterface());
                    @SuppressWarnings("unchecked")
                    AgentExtensionLoader<Object> newLoader = (AgentExtensionLoader<Object>)
                            AgentExtensionLoader.getExtensionLoader(newInterfaceClass);
                    newLoader.registerDynamicExtension(
                            newDefinition.getExtensionName(),
                            newDefinition.getClassName(),
                            newDefinition.getSourceCode()
                    );

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
     * Unregister SPI after transaction commit
     */
    private void unregisterAfterCommit(String spiInterface, String extensionName) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // 通过AgentExtensionLoader卸载扩展
                    Class<?> interfaceClass = Class.forName(spiInterface);
                    @SuppressWarnings("unchecked")
                    AgentExtensionLoader<Object> loader = (AgentExtensionLoader<Object>)
                            AgentExtensionLoader.getExtensionLoader(interfaceClass);
                    loader.removeDynamicExtension(extensionName);

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
     * 转换创建请求为DO对象
     */
    private NodeExtensionConfigDO convertCreateReqToDO(NodeExtensionConfigCreateReq request) {
        NodeExtensionConfigDO configDO = new NodeExtensionConfigDO();
        BeanUtils.copyProperties(request, configDO);
        return configDO;
    }

    /**
     * 转换更新请求为DO对象
     */
    private NodeExtensionConfigDO convertUpdateReqToDO(NodeExtensionConfigUpdateReq request) {
        NodeExtensionConfigDO configDO = new NodeExtensionConfigDO();
        BeanUtils.copyProperties(request, configDO);
        return configDO;
    }
}
