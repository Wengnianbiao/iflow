package com.helianhealth.agent.console.service.extension;

import com.helianhealth.agent.common.exception.AgentConfigException;
import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.console.model.convert.NodeExtensionConfigConvert;
import com.helianhealth.agent.console.controller.request.NodeExtensionConfigCreateReq;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigDetailResponse;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigUpdateReq;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.service.NodeExtensionConfigCommandService;
import com.helianhealth.agent.repository.service.NodeExtensionConfigQueryService;
import com.helianhealth.agent.repository.service.WorkflowNodeQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    private final NodeExtensionConfigQueryService extensionConfigQueryService;
    private final NodeExtensionConfigCommandService extensionConfigCommandService;
    private final NodeExtensionConfigService extensionNodeUpdateService;
    private final WorkflowNodeQueryService workflowNodeQueryService;
    private final NodeExtensionConfigConvert extensionConfigConvert;

    public DynamicSpiAdminService(NodeExtensionConfigQueryService extensionConfigQueryService,
                                  NodeExtensionConfigCommandService extensionConfigCommandService,
                                  NodeExtensionConfigService extensionNodeUpdateService,
                                  WorkflowNodeQueryService workflowNodeQueryService,
                                  NodeExtensionConfigConvert extensionConfigConvert) {
        this.extensionConfigQueryService = extensionConfigQueryService;
        this.extensionConfigCommandService = extensionConfigCommandService;
        this.extensionNodeUpdateService = extensionNodeUpdateService;
        this.workflowNodeQueryService = workflowNodeQueryService;
        this.extensionConfigConvert = extensionConfigConvert;
    }

    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void registerExtension(ExtensionType type, NodeExtensionConfigCreateReq request) {
        NodeExtensionConfig config = extensionConfigConvert.fromCreateReq(request);

        // Step 1: 更新节点拓展字段
        extensionNodeUpdateService.updateNodeExtensionField(
            type,
            config.getExtensionName(),
            config.getNodeId(),
            config.getExtensionParamValue()
        );

        // Step 2: 插入扩展配置到数据库
        extensionConfigCommandService.insert(config);

        // Step 3: 如果启用，注册到SPI运行时
        // 放到最后确保异常回滚事务,更新也是如此
        if (config.isEnabled()) {
            registerSpi(config);
        }

        log.info("Registered extension successfully: type={}, extensionName={}, nodeIds={}",
                type.getDescription(), config.getExtensionName(), config.getNodeId());
    }

    /**
     * 更新扩展（完整流程：清理旧节点 + 更新新节点 + 持久化 + 刷新SPI）
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void updateExtension(ExtensionType type, NodeExtensionConfigUpdateReq request) {
        NodeExtensionConfig config = extensionConfigConvert.fromUpdateReq(request);

        // Step 1: 查询旧配置,清理旧节点字段
        NodeExtensionConfig oldConfig = extensionConfigQueryService.selectById(config.getCodeId());
        if (oldConfig != null && oldConfig.getNodeId() != null) {
            extensionNodeUpdateService.clearNodeFields(
                type,
                oldConfig.getExtensionName(),
                oldConfig.getNodeId()
            );
        }

        // Step 2: 更新新节点字段
        extensionNodeUpdateService.updateNodeExtensionField(
            type,
            config.getExtensionName(),
            config.getNodeId(),
            config.getExtensionParamValue()
        );

        // Step 3: 更新数据库
        extensionConfigCommandService.update(config);

        // Step 4: 卸载旧SPI并注册新SPI（失败则异常回滚整个事务）
        if (oldConfig != null) {
            unregisterSpi(oldConfig);
        }
        registerSpi(config);

        log.info("Updated extension successfully: type={}, extensionName={}, nodeIds={}",
                type.getDescription(), config.getExtensionName(), config.getNodeId());
    }

    /**
     * 保存扩展配置并注册SPI（不包含节点字段更新）
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void saveAndRegisterExtension(NodeExtensionConfig config) {
        extensionConfigCommandService.insert(config);

        if (config.isEnabled()) {
            registerSpi(config);
        }

        log.info("Saved extension successfully: extensionName={}, nodeIds={}",
                config.getExtensionName(), config.getNodeId());
    }

    /**
     * 更新扩展配置并刷新SPI
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void updateAndRefreshExtension(NodeExtensionConfig oldConfig, NodeExtensionConfig newConfig) {
        extensionConfigCommandService.update(newConfig);

        if (oldConfig != null) {
            unregisterSpi(oldConfig);
        }
        registerSpi(newConfig);

        log.info("Updated extension successfully: extensionName={}, nodeIds={}",
                newConfig.getExtensionName(), newConfig.getNodeId());
    }

    /**
     * 根据ID查询扩展配置
     */
    public NodeExtensionConfig getExtensionById(Integer codeId) {
        return extensionConfigQueryService.selectById(codeId);
    }

    /**
     * 查询扩展配置详情（带节点参数值）
     * 从关联节点的 extension_param 中提取该 filter 的实际配置值
     *
     * @param codeId 扩展配置ID
     * @return 扩展配置详情响应对象
     */
    public NodeExtensionConfigDetailResponse getExtensionDetailById(Integer codeId) {
        NodeExtensionConfig config = extensionConfigQueryService.selectById(codeId);
        if (config == null) {
            return null;
        }

        // 只有 Filter 类型才需要从节点提取参数
        if (ExtensionType.FILTER.getSpiInterface().equals(config.getSpiInterface())) {
            List<Integer> nodeIds = config.getNodeId();
            if (nodeIds != null && !nodeIds.isEmpty()) {
                // 从第一个节点获取 extension_param
                Integer firstNodeId = nodeIds.get(0);
                WorkflowNode node = workflowNodeQueryService.selectByNodeId(firstNodeId);
                if (node != null && node.getExtensionParam() != null) {
                    String filterKey = "filter." + config.getExtensionName();
                    String paramValue = node.getExtensionParam().get(filterKey);
                    // 设置到 Domain 对象中（用于转换）
                    config.setExtensionParamValue(paramValue);
                }
            }
        }

        // 转换为 Response 对象
        return extensionConfigConvert.doConvertToDetailResponse(config);
    }

    /**
     * 刷新指定扩展的SPI运行时
     */
    public void refresh(Integer codeId) {
        NodeExtensionConfig config = extensionConfigQueryService.selectById(codeId);
        if (config == null) {
            throw new AgentException(AgentErrorCode.INTERNAL_ERROR, "扩展配置不存在: " + codeId);
        }

        try {
            AgentExtensionLoader<?> loader = getLoader(config.getSpiInterface());
            boolean dynamicCompile = "ONLINE".equals(config.getExtensionMode());
            loader.refreshDynamicExtension(
                    config.getExtensionName(),
                    config.getClassName(),
                    config.getSourceCode(),
                    dynamicCompile
            );
        } catch (Exception e) {
            throw new AgentException(AgentErrorCode.INTERNAL_ERROR, "SPI刷新失败: " + config.getSpiInterface() + "#" + config.getExtensionName(), e);
        }

        log.info("Refreshed extension successfully: interface={}, extension={}",
                config.getSpiInterface(), config.getExtensionName());
    }

    /**
     * 禁用并卸载SPI运行时实现
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void disableAndUnregister(Integer codeId) {
        NodeExtensionConfig config = extensionConfigQueryService.selectById(codeId);
        if (config == null) {
            return;
        }

        config.setEnabled(0);
        extensionConfigCommandService.update(config);
        unregisterSpi(config);

        log.info("Disabled extension successfully: codeId={}, extensionName={}",
                codeId, config.getExtensionName());
    }

    /**
     * 启用并注册SPI到运行时
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void enableAndRegister(Integer codeId) {
        NodeExtensionConfig config = extensionConfigQueryService.selectById(codeId);
        if (config == null) {
            return;
        }

        config.setEnabled(1);
        extensionConfigCommandService.update(config);
        registerSpi(config);

        log.info("Enabled extension successfully: codeId={}, extensionName={}",
                codeId, config.getExtensionName());
    }

    /**
     * 物理删除扩展配置（清理节点字段 + 卸载SPI + 删除数据库记录）
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void deleteExtension(Integer codeId) {
        NodeExtensionConfig config = extensionConfigQueryService.selectById(codeId);
        if (config == null) {
            return;
        }

        // Step 1: 清理关联节点字段
        if (config.getNodeId() != null && !config.getNodeId().isEmpty()) {
            ExtensionType type = ExtensionType.fromSpiInterface(config.getSpiInterface());
            extensionNodeUpdateService.clearNodeFields(type, config.getExtensionName(), config.getNodeId());
        }

        // Step 2: 卸载SPI运行时
        if (config.isEnabled()) {
            unregisterSpi(config);
        }

        // Step 3: 物理删除数据库记录
        extensionConfigCommandService.deleteById(codeId);

        log.info("Deleted extension successfully: codeId={}, extensionName={}",
                codeId, config.getExtensionName());
    }

    /**
     * List all enabled configurations from database
     */
    public List<NodeExtensionConfig> listEnabledConfigs() {
        return extensionConfigQueryService.selectByEnabled(1);
    }

    /**
     * 根据 SPI 接口类型查询扩展配置列表
     */
    public List<NodeExtensionConfig> listExtensionsBySpiInterface(String spiInterface) {
        return extensionConfigQueryService.selectBySpiInterface(spiInterface);
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
     * 注册SPI到运行时
     */
    private void registerSpi(NodeExtensionConfig config) {
        try {
            AgentExtensionLoader<?> loader = getLoader(config.getSpiInterface());
            // 是否动态编译
            boolean dynamicCompile = "ONLINE".equals(config.getExtensionMode());
            loader.registerDynamicExtension(
                    config.getExtensionName(),
                    config.getClassName(),
                    config.getSourceCode(),
                    dynamicCompile
            );
        } catch (Exception e) {
            log.error("SPI注册失败: {}#{},{}", config.getSpiInterface(), config.getExtensionName(), e.getMessage(), e);
            throw new AgentConfigException(AgentErrorCode.SPI_REGISTER_FAILED,
                    config.getSpiInterface() + "#" + config.getExtensionName() + e.getMessage(), e);
        }
    }

    /**
     * 从运行时卸载SPI
     */
    private void unregisterSpi(NodeExtensionConfig config) {
        AgentExtensionLoader<?> loader = getLoader(config.getSpiInterface());
        loader.removeDynamicExtension(config.getExtensionName());
    }

    /**
     * 获取指定SPI接口的ExtensionLoader
     */
    private AgentExtensionLoader<?> getLoader(String spiInterface) {
        try {
            Class<?> interfaceClass = Class.forName(spiInterface);
            return AgentExtensionLoader.getExtensionLoader(interfaceClass);
        } catch (ClassNotFoundException e) {
            throw new AgentException(AgentErrorCode.INTERNAL_ERROR, "SPI接口类未找到: " + spiInterface, e);
        }
    }

}
