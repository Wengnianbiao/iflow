package com.helianhealth.agent.extension.spi.dynamic;

import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.common.extension.AgentSPI;
import com.helianhealth.agent.common.extension.ExtensionRegistry;
import com.helianhealth.agent.extension.spi.model.DynamicSpiDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic SPI Manager
 * 
 * 动态SPI管理器，面向workflow层提供SPI扩展的动态注册管理能力
 * 
 * 核心职责：
 * 1. 接收 DynamicSpiDefinition 进行扩展注册
 * 2. 委托 ExtensionRegistry 完成实际的加载和注册
 * 3. 维护动态注册扩展的元数据缓存
 * 
 * 设计说明：
 * - extension模块仅负责SPI扩展类的注册与内存加载
 * - 不处理业务节点逻辑和数据库相关字段
 */
public class DynamicSpiManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicSpiManager.class);

    /**
     * 已注册的动态扩展缓存
     * key: spiInterface#extensionName, value: Class对象
     */
    private final Map<String, Class<?>> registeredExtensions = new ConcurrentHashMap<>();

    /**
     * 扩展注册中心
     */
    private final ExtensionRegistry registry;

    public DynamicSpiManager() {
        this.registry = ExtensionRegistry.getInstance();
    }

    /**
     * 加载并注册动态SPI扩展
     * 
     * 支持两种模式：
     * 1. 动态编译模式：sourceCode不为空时，从源码编译
     * 2. 类路径模式：sourceCode为空时，从类路径加载预编译类
     *
     * @param dynamicSpi SPI定义
     */
    public void loadAndRegister(DynamicSpiDefinition dynamicSpi) throws Exception {
        String className = dynamicSpi.getClassName();
        String sourceCode = dynamicSpi.getSourceCode();
        String spiInterface = dynamicSpi.getSpiInterface();
        String extensionName = dynamicSpi.getExtensionName();

        log.debug("Loading dynamic SPI: interface={}, name={}, class={}",
                spiInterface, extensionName, className);

        // 委托ExtensionRegistry进行加载和注册
        registry.loadAndRegister(spiInterface, extensionName, className, sourceCode);

        // 更新本地缓存
        String cacheKey = buildCacheKey(spiInterface, extensionName);
        try {
            Class<?> clazz = Class.forName(className);
            registeredExtensions.put(cacheKey, clazz);
        } catch (ClassNotFoundException e) {
            // 动态编译的类可能无法通过Class.forName获取，忽略
            log.debug("Class not in classpath (expected for dynamic compiled): {}", className);
        }

        log.info("Registered dynamic SPI: {}", cacheKey);
    }

    /**
     * 批量加载SPI扩展
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

    /**
     * 检查扩展是否已注册
     */
    public boolean hasExtension(String spiInterface, String extensionName) {
        return registry.hasExtension(spiInterface, extensionName);
    }

    /**
     * 获取扩展实例
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtension(String spiInterface, String extensionName) throws Exception {
        // 验证接口必须标注@AgentSPI
        Class<?> interfaceClass = Class.forName(spiInterface);
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException("spiInterface must be an interface type: " + spiInterface);
        }
        if (!interfaceClass.isAnnotationPresent(AgentSPI.class)) {
            throw new IllegalArgumentException("spiInterface must be annotated with @AgentSPI: " + spiInterface);
        }

        // 通过AgentExtensionLoader获取扩展
        AgentExtensionLoader<?> loader = AgentExtensionLoader.getExtensionLoader(interfaceClass);
        return (T) loader.getExtension(extensionName);
    }

    /**
     * 刷新SPI扩展（卸载后重新加载）
     */
    public void refresh(DynamicSpiDefinition dynamicSpi) throws Exception {
        // 先卸载旧的
        remove(dynamicSpi.getSpiInterface(), dynamicSpi.getExtensionName());

        // 重新加载
        loadAndRegister(dynamicSpi);

        String cacheKey = buildCacheKey(dynamicSpi.getSpiInterface(), dynamicSpi.getExtensionName());
        log.info("Successfully refreshed dynamic SPI: {}", cacheKey);
    }

    /**
     * 移除动态SPI扩展
     *
     * @param spiInterface 接口全限定名
     * @param extensionName 扩展名称
     */
    public void remove(String spiInterface, String extensionName) {
        String cacheKey = buildCacheKey(spiInterface, extensionName);
        
        // 从本地缓存移除
        registeredExtensions.remove(cacheKey);
        
        // 委托ExtensionRegistry移除
        registry.removeExtension(spiInterface, extensionName);

        // 清除AgentExtensionLoader的缓存
        try {
            Class<?> interfaceClass = Class.forName(spiInterface);
            if (interfaceClass.isAnnotationPresent(AgentSPI.class)) {
                AgentExtensionLoader<?> loader = AgentExtensionLoader.getExtensionLoader(interfaceClass);
                loader.clearCache();
            }
        } catch (ClassNotFoundException e) {
            log.warn("Interface class not found when clearing cache: {}", spiInterface);
        }

        log.info("Removed dynamic SPI: {}", cacheKey);
    }

    /**
     * 获取所有已注册的动态扩展
     */
    public Map<String, Class<?>> getAllRegisteredExtensions() {
        return new ConcurrentHashMap<>(registeredExtensions);
    }

    /**
     * 清空所有动态扩展
     */
    public void clear() {
        int count = registeredExtensions.size();
        if (count > 0) {
            log.info("Clearing {} dynamic SPIs", count);
        }

        // 逐个移除以确保正确清理
        for (String key : registeredExtensions.keySet()) {
            String[] parts = key.split("#");
            if (parts.length == 2) {
                registry.removeExtension(parts[0], parts[1]);
            }
        }

        registeredExtensions.clear();

        // 触发GC
        System.gc();
    }

    private String buildCacheKey(String spiInterface, String extensionName) {
        return spiInterface + "#" + extensionName;
    }
}
