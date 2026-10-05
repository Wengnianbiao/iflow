package com.helianhealth.iflow.tasks.spi;

import com.helianhealth.iflow.common.extension.ExtensionLoader;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按 {@code inputParameters.remoteExtension} 路由 {@link InterfaceRemoteInvoker} 实现。
 */
@Component
public class SpiExtensionRouter {

    private final ConcurrentMap<String, Object> extensionLocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SpiTaskInput.ExtensionDefinition> registeredDefinitions =
            new ConcurrentHashMap<>();

    public Object invoke(String remoteExtension, Map<String, Object> spiInput) {
        return invoke(remoteExtension, null, spiInput);
    }

    /** 在执行 SPI 节点时注册/刷新节点内嵌实现，然后立即调用。 */
    public Object invoke(
            String remoteExtension,
            SpiTaskInput.ExtensionDefinition definition,
            Map<String, Object> spiInput) {
        String extensionName =
                definition == null ? remoteExtension : definition.remoteExtension();
        if (extensionName == null || extensionName.isBlank()) {
            return resolve(null).remoteInvoke(spiInput);
        }

        String normalizedName = extensionName.trim();
        Object lock = extensionLocks.computeIfAbsent(normalizedName, ignored -> new Object());
        synchronized (lock) {
            if (definition != null) {
                registerOrRefresh(definition);
            }
            return resolve(normalizedName).remoteInvoke(spiInput);
        }
    }

    private void registerOrRefresh(SpiTaskInput.ExtensionDefinition definition) {
        ExtensionLoader<InterfaceRemoteInvoker> loader = loader();
        SpiTaskInput.ExtensionDefinition registered =
                registeredDefinitions.get(definition.remoteExtension());
        if (definition.equals(registered) && loader.hasExtension(definition.remoteExtension())) {
            return;
        }

        try {
            if (loader.hasExtension(definition.remoteExtension())) {
                loader.refreshDynamicExtension(
                        definition.remoteExtension(),
                        definition.className(),
                        definition.sourceCode(),
                        definition.online());
            } else {
                loader.registerDynamicExtension(
                        definition.remoteExtension(),
                        definition.className(),
                        definition.sourceCode(),
                        definition.online());
            }
            // 立即实例化，以便在任务执行阶段暴露构造器和依赖注入错误。
            loader.getExtension(definition.remoteExtension());
            registeredDefinitions.put(definition.remoteExtension(), definition);
        } catch (Exception ex) {
            registeredDefinitions.remove(definition.remoteExtension());
            throw new IllegalStateException(
                    "Unable to load SPI extension " + definition.remoteExtension(), ex);
        }
    }

    InterfaceRemoteInvoker resolve(String remoteExtension) {
        ExtensionLoader<InterfaceRemoteInvoker> loader = loader();
        if (remoteExtension == null || remoteExtension.isBlank()) {
            return loader.getDefaultExtension();
        }
        return loader.getExtension(remoteExtension.trim());
    }

    private ExtensionLoader<InterfaceRemoteInvoker> loader() {
        return ExtensionLoader.getExtensionLoader(InterfaceRemoteInvoker.class);
    }
}
