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
import org.springframework.context.annotation.Lazy;
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
    private final WorkflowExtensionBindingService workflowExtensionBindingService;

    public DynamicSpiAdminService(NodeExtensionConfigQueryService extensionConfigQueryService,
                                  NodeExtensionConfigCommandService extensionConfigCommandService,
                                  NodeExtensionConfigService extensionNodeUpdateService,
                                  WorkflowNodeQueryService workflowNodeQueryService,
                                  NodeExtensionConfigConvert extensionConfigConvert,
                                  @Lazy WorkflowExtensionBindingService workflowExtensionBindingService) {
        this.extensionConfigQueryService = extensionConfigQueryService;
        this.extensionConfigCommandService = extensionConfigCommandService;
        this.extensionNodeUpdateService = extensionNodeUpdateService;
        this.workflowNodeQueryService = workflowNodeQueryService;
        this.extensionConfigConvert = extensionConfigConvert;
        this.workflowExtensionBindingService = workflowExtensionBindingService;
    }

    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void registerExtension(ExtensionType type, NodeExtensionConfigCreateReq request) {
        NodeExtensionConfig config = extensionConfigConvert.fromCreateReq(request);

        // Step 1: 需要时更新节点拓展字段
        if (isNodeScopedType(type)) {
            extensionNodeUpdateService.updateNodeExtensionField(
                type,
                config.getExtensionName(),
                config.getBindingIds(),
                config.getExtensionParamValue()
            );
        }

        // Step 2: 插入扩展配置到数据库
        extensionConfigCommandService.insert(config);

        // Step 3: 如果启用，注册到SPI运行时
        // 放到最后确保异常回滚事务,更新也是如此
        if (config.isEnabled()) {
            registerSpi(config);
        }

        log.info("Registered extension successfully: type={}, extensionName={}, bindingIds={}",
                type.getDescription(), config.getExtensionName(), config.getBindingIds());

        if (isWorkflowRouteHandlerType(type) && config.isEnabled()) {
            workflowExtensionBindingService.syncRouteExtensionBinding(
                    request.getBindFlowId(),
                    request.getExtensionName(),
                    null);
        }
    }

    /**
     * 更新扩展（完整流程：清理旧节点 + 更新新节点 + 持久化 + 刷新SPI）
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void updateExtension(ExtensionType type, NodeExtensionConfigUpdateReq request) {
        NodeExtensionConfig config = extensionConfigConvert.fromUpdateReq(request);

        // Step 1: 查询旧配置,清理旧节点字段
        NodeExtensionConfig oldConfig = extensionConfigQueryService.selectById(config.getConfigId());
        if (isNodeScopedType(type) && oldConfig != null
                && oldConfig.getBindingIds() != null && !oldConfig.getBindingIds().isEmpty()) {
            extensionNodeUpdateService.clearNodeFields(
                type,
                oldConfig.getExtensionName(),
                oldConfig.getBindingIds()
            );
        }

        // Step 2: 需要时更新新节点字段
        if (isNodeScopedType(type)) {
            extensionNodeUpdateService.updateNodeExtensionField(
                type,
                config.getExtensionName(),
                config.getBindingIds(),
                config.getExtensionParamValue()
            );
        }

        // Step 3: 更新数据库
        extensionConfigCommandService.update(config);

        // Step 4: 卸载旧SPI并注册新SPI（失败则异常回滚整个事务）
        if (oldConfig != null) {
            unregisterSpi(oldConfig);
        }
        registerSpi(config);

        log.info("Updated extension successfully: type={}, extensionName={}, bindingIds={}",
                type.getDescription(), config.getExtensionName(), config.getBindingIds());

        if (isWorkflowRouteHandlerType(type) && config.isEnabled()) {
            workflowExtensionBindingService.syncRouteExtensionBinding(
                    request.getBindFlowId(),
                    request.getExtensionName(),
                    oldConfig != null ? oldConfig.getExtensionName() : null);
        }
    }

    /**
     * 根据ID查询扩展配置
     */
    public NodeExtensionConfig getExtensionById(Integer configId) {
        return extensionConfigQueryService.selectById(configId);
    }

    /**
     * 查询扩展配置详情（带节点参数值）
     * 从关联节点的 extension_param 中提取该 filter 的实际配置值
     *
     * @param configId 扩展配置主键（config_id）
     * @return 扩展配置详情响应对象
     */
    public NodeExtensionConfigDetailResponse getExtensionDetailById(Integer configId) {
        NodeExtensionConfig config = extensionConfigQueryService.selectById(configId);
        if (config == null) {
            return null;
        }

        // 只有 Filter 类型才需要从节点提取参数
        if (ExtensionType.FILTER.getSpiInterface().equals(config.getSpiInterface())) {
            List<Integer> nodeIds = config.getBindingIds();
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
     * 禁用并卸载SPI运行时实现
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void disableAndUnregister(Integer configId) {
        NodeExtensionConfig config = extensionConfigQueryService.selectById(configId);
        if (config == null) {
            return;
        }

        if (config.getBindingIds() != null && !config.getBindingIds().isEmpty()) {
            ExtensionType type = ExtensionType.fromSpiInterface(config.getSpiInterface());
            extensionNodeUpdateService.clearNodeFields(type, config.getExtensionName(), config.getBindingIds());
        }

        config.setEnabled(0);
        extensionConfigCommandService.update(config);
        unregisterSpi(config);

        log.info("Disabled extension successfully: configId={}, extensionName={}",
                configId, config.getExtensionName());
    }

    /**
     * 启用并注册SPI到运行时
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void enableAndRegister(Integer configId) {
        NodeExtensionConfig config = extensionConfigQueryService.selectById(configId);
        if (config == null) {
            return;
        }

        // 恢复节点字段，确保运行时能找到扩展
        if (config.getBindingIds() != null && !config.getBindingIds().isEmpty()) {
            ExtensionType type = ExtensionType.fromSpiInterface(config.getSpiInterface());
            extensionNodeUpdateService.updateNodeExtensionField(
                type, config.getExtensionName(), config.getBindingIds(), config.getExtensionParamValue());
        }

        config.setEnabled(1);
        extensionConfigCommandService.update(config);
        registerSpi(config);

        log.info("Enabled extension successfully: configId={}, extensionName={}",
                configId, config.getExtensionName());
    }

    /**
     * 物理删除扩展配置（清理节点字段 + 卸载SPI + 删除数据库记录）
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void deleteExtension(Integer configId) {
        NodeExtensionConfig config = extensionConfigQueryService.selectById(configId);
        if (config == null) {
            return;
        }

        // Step 1: 清理关联节点字段
        if (config.getBindingIds() != null && !config.getBindingIds().isEmpty()) {
            ExtensionType type = ExtensionType.fromSpiInterface(config.getSpiInterface());
            extensionNodeUpdateService.clearNodeFields(type, config.getExtensionName(), config.getBindingIds());
        }

        // Step 2: 卸载SPI运行时
        if (config.isEnabled()) {
            unregisterSpi(config);
        }

        // Step 3: 物理删除数据库记录
        extensionConfigCommandService.deleteById(configId);

        log.info("Deleted extension successfully: configId={}, extensionName={}",
                configId, config.getExtensionName());
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
            "com.helianhealth.agent.invocation.remote.filter.AgentFilter",
            "com.helianhealth.agent.api.OpenApiRouteHandler",
            "com.helianhealth.agent.api.JarvisRouteHandler"
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

    private boolean isNodeScopedType(ExtensionType type) {
        return type == ExtensionType.REMOTE || type == ExtensionType.FILTER;
    }

    private boolean isWorkflowRouteHandlerType(ExtensionType type) {
        return type == ExtensionType.WORKFLOW || type == ExtensionType.JARVIS_WORKFLOW;
    }

}
