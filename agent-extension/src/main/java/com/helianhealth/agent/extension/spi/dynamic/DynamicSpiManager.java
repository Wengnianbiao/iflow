package com.helianhealth.agent.extension.spi.dynamic;

import com.helianhealth.agent.extension.spi.model.DynamicSpiDefinition;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态 SPI 管理器
 * 负责管理从配置仓储加载的动态 SPI 实现类
 */
@Slf4j
public class DynamicSpiManager {

    private final DynamicClassLoader classLoader;

    /**
     * 已注册的动态扩展缓存
     * key: spiInterface#extensionName, value: Class 对象
     */
    private final Map<String, Class<?>> registeredExtensions = new ConcurrentHashMap<>();

    public DynamicSpiManager() {
        this.classLoader = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());
    }

    public Class<?> loadAndRegister(DynamicSpiDefinition dynamicSpi) throws Exception {
        String className = dynamicSpi.getClassName();
        String sourceCode = dynamicSpi.getSourceCode();
        String spiInterface = dynamicSpi.getSpiInterface();
        String extensionName = dynamicSpi.getExtensionName();

        log.info("Start loading dynamic SPI: spiInterface={}, extensionName={}, className={}",
                spiInterface, extensionName, className);

        Class<?> clazz = classLoader.loadClass(className, sourceCode);

        Class<?> interfaceClass = Class.forName(spiInterface);
        if (!interfaceClass.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException(
                    String.format("Class %s does not implement SPI interface %s", className, spiInterface));
        }

        // Register to Dubbo ExtensionLoader
        ExtensionLoader<?> loader = ExtensionLoader.getExtensionLoader(interfaceClass);
        loader.addExtension(extensionName, clazz);

        String cacheKey = buildCacheKey(spiInterface, extensionName);
        registeredExtensions.put(cacheKey, clazz);

        log.info("Successfully registered dynamic SPI: {}", cacheKey);
        return clazz;
    }

    /**
     * 批量加载 SPI 扩展（由 workflow 层保证只传入启用的配置）
     */
    public void batchLoad(List<DynamicSpiDefinition> dynamicSpiDefinitions) {
        for (DynamicSpiDefinition def : dynamicSpiDefinitions) {
            try {
                loadAndRegister(def);
            } catch (Exception e) {
                log.error("Failed to load dynamic SPI: extensionName={}, className={}",
                        def.getExtensionName(), def.getClassName(), e);
            }
        }
    }

    public boolean hasExtension(String spiInterface, String extensionName) {
        String cacheKey = buildCacheKey(spiInterface, extensionName);
        return registeredExtensions.containsKey(cacheKey);
    }

    @SuppressWarnings("unchecked")
    public <T> T getExtension(String spiInterface, String extensionName) throws Exception {
        String cacheKey = buildCacheKey(spiInterface, extensionName);
        Class<?> clazz = registeredExtensions.get(cacheKey);
        if (clazz == null) {
            return null;
        }

        Class<?> interfaceClass = Class.forName(spiInterface);
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException("spiInterface must be an interface type: " + spiInterface);
        }

        ExtensionLoader<?> loader = ExtensionLoader.getExtensionLoader(interfaceClass);
        return (T) loader.getExtension(extensionName);
    }

    /**
     * 刷新 SPI 扩展（卸载后重新加载）
     */
    public void refresh(DynamicSpiDefinition dynamicSpi) throws Exception {
        String cacheKey = buildCacheKey(dynamicSpi.getSpiInterface(), dynamicSpi.getExtensionName());
        registeredExtensions.remove(cacheKey);
        DynamicClassLoader.clearCache(dynamicSpi.getClassName());
        
        loadAndRegister(dynamicSpi);
        log.info("Successfully refreshed dynamic SPI: {}", cacheKey);
    }

    public void remove(String spiInterface, String extensionName) {
        String cacheKey = buildCacheKey(spiInterface, extensionName);
        registeredExtensions.remove(cacheKey);
        log.info("Removed dynamic SPI: {}", cacheKey);
    }

    private String buildCacheKey(String spiInterface, String extensionName) {
        return spiInterface + "#" + extensionName;
    }

    public Map<String, Class<?>> getAllRegisteredExtensions() {
        return new ConcurrentHashMap<>(registeredExtensions);
    }

    public void clear() {
        registeredExtensions.clear();
        DynamicClassLoader.clearCache();
        log.info("Cleared all dynamic SPIs");
    }
}
