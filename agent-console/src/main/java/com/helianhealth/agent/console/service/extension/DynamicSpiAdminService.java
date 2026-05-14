package com.helianhealth.agent.console.service.extension;

import com.helianhealth.agent.common.exception.AgentConfigException;
import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.console.controller.request.NodeExtensionConfigCreateReq;
import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.console.model.convert.NodeExtensionConfigConvert;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigDetailResponse;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigUpdateReq;
import com.helianhealth.agent.console.service.extension.strategy.ExtensionTypeStrategy;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.repository.service.NodeExtensionConfigCommandService;
import com.helianhealth.agent.repository.service.NodeExtensionConfigQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamic SPI Admin Service
 * <p>职责：
 * <ul>
 *   <li>主干流程：扩展配置的本表 CRUD + SPI 运行时注册/卸载（所有类型统一）。</li>
 *   <li>类型差异（节点关联字段、工作流路由绑定、详情回显）委派给 {@link ExtensionTypeStrategy}。</li>
 * </ul>
 */
@Service
@Slf4j
public class DynamicSpiAdminService {

    private final NodeExtensionConfigQueryService extensionConfigQueryService;
    private final NodeExtensionConfigCommandService extensionConfigCommandService;
    private final NodeExtensionConfigConvert extensionConfigConvert;
    private final Map<ExtensionType, ExtensionTypeStrategy> strategies;

    public DynamicSpiAdminService(NodeExtensionConfigQueryService extensionConfigQueryService,
                                  NodeExtensionConfigCommandService extensionConfigCommandService,
                                  NodeExtensionConfigConvert extensionConfigConvert,
                                  List<ExtensionTypeStrategy> strategyList) {
        this.extensionConfigQueryService = extensionConfigQueryService;
        this.extensionConfigCommandService = extensionConfigCommandService;
        this.extensionConfigConvert = extensionConfigConvert;
        this.strategies = new EnumMap<>(ExtensionType.class);
        for (ExtensionTypeStrategy strategy : strategyList) {
            this.strategies.put(strategy.supportType(), strategy);
        }
    }

    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void registerExtension(ExtensionType type, NodeExtensionConfigCreateReq request) {
        ExtensionTypeStrategy strategy = strategyOf(type);
        NodeExtensionConfig config = extensionConfigConvert.fromCreateReq(request);

        // Step 1: 策略侧写关联字段
        strategy.onRegister(config);

        // Step 2: 持久化本表
        extensionConfigCommandService.insert(config);

        // Step 3: 启用态时注册到 SPI 运行时（放末位确保前序失败可事务回滚）
        if (config.isEnabled()) {
            registerSpi(config);
        }

        log.info("Registered extension successfully: type={}, extensionName={}, bindingIds={}",
                type.getDescription(), config.getExtensionName(), config.getBindingIds());
    }

    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void updateExtension(ExtensionType type, NodeExtensionConfigUpdateReq request) {
        ExtensionTypeStrategy strategy = strategyOf(type);
        NodeExtensionConfig newConfig = extensionConfigConvert.fromUpdateReq(request);
        NodeExtensionConfig oldConfig = extensionConfigQueryService.selectById(newConfig.getConfigId());

        // Step 1: 策略侧替换关联字段（清旧 + 写新）
        strategy.onUpdate(newConfig, oldConfig);

        // Step 2: 更新本表
        extensionConfigCommandService.update(newConfig);

        // Step 3: 卸载旧 SPI + 注册新 SPI
        if (oldConfig != null) {
            unregisterSpi(oldConfig);
        }
        registerSpi(newConfig);

        log.info("Updated extension successfully: type={}, extensionName={}, bindingIds={}",
                type.getDescription(), newConfig.getExtensionName(), newConfig.getBindingIds());
    }

    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void disableAndUnregister(Integer configId) {
        NodeExtensionConfig config = extensionConfigQueryService.selectById(configId);
        if (config == null) {
            return;
        }

        strategyOf(ExtensionType.fromSpiInterface(config.getSpiInterface())).onUnregister(config);

        config.setEnabled(0);
        extensionConfigCommandService.update(config);
        unregisterSpi(config);

        log.info("Disabled extension successfully: configId={}, extensionName={}",
                configId, config.getExtensionName());
    }

    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void enableAndRegister(Integer configId) {
        NodeExtensionConfig config = extensionConfigQueryService.selectById(configId);
        if (config == null) {
            return;
        }

        // 启用时：关联字段根据持久化的 config.bindingIds 恢复
        strategyOf(ExtensionType.fromSpiInterface(config.getSpiInterface())).onRegister(config);

        config.setEnabled(1);
        extensionConfigCommandService.update(config);
        registerSpi(config);

        log.info("Enabled extension successfully: configId={}, extensionName={}",
                configId, config.getExtensionName());
    }

    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void deleteExtension(Integer configId) {
        NodeExtensionConfig config = extensionConfigQueryService.selectById(configId);
        if (config == null) {
            return;
        }

        strategyOf(ExtensionType.fromSpiInterface(config.getSpiInterface())).onUnregister(config);

        if (config.isEnabled()) {
            unregisterSpi(config);
        }

        extensionConfigCommandService.deleteById(configId);

        log.info("Deleted extension successfully: configId={}, extensionName={}",
                configId, config.getExtensionName());
    }

    public NodeExtensionConfig getExtensionById(Integer configId) {
        return extensionConfigQueryService.selectById(configId);
    }

    public NodeExtensionConfigDetailResponse getExtensionDetailById(Integer configId) {
        NodeExtensionConfig config = extensionConfigQueryService.selectById(configId);
        if (config == null) {
            return null;
        }
        strategyOf(ExtensionType.fromSpiInterface(config.getSpiInterface())).enrichDetail(config);
        return extensionConfigConvert.doConvertToDetailResponse(config);
    }

    public List<NodeExtensionConfig> listEnabledConfigs() {
        return extensionConfigQueryService.selectByEnabled(1);
    }

    public List<NodeExtensionConfig> listExtensionsBySpiInterface(String spiInterface) {
        return extensionConfigQueryService.selectBySpiInterface(spiInterface);
    }

    public List<String> getSupportedSpiInterfaces() {
        return Arrays.asList(
                ExtensionType.REMOTE.getSpiInterface(),
                ExtensionType.FILTER.getSpiInterface(),
                ExtensionType.WORKFLOW.getSpiInterface(),
                ExtensionType.JARVIS_WORKFLOW.getSpiInterface()
        );
    }

    private ExtensionTypeStrategy strategyOf(ExtensionType type) {
        ExtensionTypeStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new AgentException(AgentErrorCode.UNSUPPORTED_EXTENSION_TYPE,
                    "未注册的扩展类型策略: " + type);
        }
        return strategy;
    }

    private void registerSpi(NodeExtensionConfig config) {
        try {
            AgentExtensionLoader<?> loader = getLoader(config.getSpiInterface());
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

    private void unregisterSpi(NodeExtensionConfig config) {
        AgentExtensionLoader<?> loader = getLoader(config.getSpiInterface());
        loader.removeDynamicExtension(config.getExtensionName());
    }

    private AgentExtensionLoader<?> getLoader(String spiInterface) {
        try {
            Class<?> interfaceClass = Class.forName(spiInterface);
            return AgentExtensionLoader.getExtensionLoader(interfaceClass);
        } catch (ClassNotFoundException e) {
            throw new AgentException(AgentErrorCode.INTERNAL_ERROR, "SPI接口类未找到: " + spiInterface, e);
        }
    }
}
