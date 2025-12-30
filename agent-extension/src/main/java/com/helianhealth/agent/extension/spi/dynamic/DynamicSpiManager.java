package com.helianhealth.agent.extension.spi.dynamic;

import com.helianhealth.agent.extension.spi.model.DynamicSpiDefinition;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.extension.ExtensionLoader;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态 SPI 管理器（重构版）
 * 使用隔离型 ClassLoader 架构，支持类的完全卸载
 * 
 * 核心设计：
 * 1. 每个动态类使用独立的 DynamicClassLoader 实例
 * 2. ClassLoader 与类一对一绑定，同生共死
 * 3. 卸载时释放 ClassLoader，触发 JVM 类卸载机制
 */
@Slf4j
public class DynamicSpiManager {

    /**
     * 已注册的动态扩展缓存
     * key: spiInterface#extensionName, value: Class 对象
     */
    private final Map<String, Class<?>> registeredExtensions = new ConcurrentHashMap<>();
    
    /**
     * ClassLoader 缓存（与类一对一绑定）
     * key: className, value: DynamicClassLoader
     */
    private final Map<String, DynamicClassLoader> classLoaderMap = new ConcurrentHashMap<>();
    
    /**
     * 父 ClassLoader（用于创建隔离的 ClassLoader）
     */
    private final ClassLoader parentClassLoader;

    public DynamicSpiManager() {
        this.parentClassLoader = Thread.currentThread().getContextClassLoader();
    }

    public Class<?> loadAndRegister(DynamicSpiDefinition dynamicSpi) throws Exception {
        String className = dynamicSpi.getClassName();
        String sourceCode = dynamicSpi.getSourceCode();
        String spiInterface = dynamicSpi.getSpiInterface();
        String extensionName = dynamicSpi.getExtensionName();

        log.info("Start loading dynamic SPI: spiInterface={}, extensionName={}, className={}",
                spiInterface, extensionName, className);

        // 为每个类创建独立的 ClassLoader
        DynamicClassLoader classLoader = new DynamicClassLoader(parentClassLoader, className);
        Class<?> clazz = classLoader.compileAndLoad(sourceCode);
        
        // 缓存 ClassLoader（与类绑定）
        classLoaderMap.put(className, classLoader);

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

        log.info("Successfully registered dynamic SPI: {}, ClassLoader: {}", 
                cacheKey, Integer.toHexString(classLoader.hashCode()));
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
        // 先卸载旧的
        remove(dynamicSpi.getSpiInterface(), dynamicSpi.getExtensionName());
        
        // 重新加载
        loadAndRegister(dynamicSpi);
        
        String cacheKey = buildCacheKey(dynamicSpi.getSpiInterface(), dynamicSpi.getExtensionName());
        log.info("Successfully refreshed dynamic SPI: {}", cacheKey);
    }

    /**
     * 移除动态 SPI 扩展，并完全卸载类
     * 
     * 卸载步骤：
     * 1. 从 registeredExtensions 缓存中移除
     * 2. 清理 Dubbo ExtensionLoader 的内部缓存
     * 3. 释放 DynamicClassLoader（断开所有引用）
     * 4. 触发 GC，让 JVM 卸载类
     * 
     * @param spiInterface 接口全限定名
     * @param extensionName 扩展名称
     */
    public void remove(String spiInterface, String extensionName) {
        String cacheKey = buildCacheKey(spiInterface, extensionName);
        Class<?> clazz = registeredExtensions.remove(cacheKey);
        
        if (clazz != null) {
            String className = clazz.getName();
            
            // 1. 从 Dubbo ExtensionLoader 中移除扩展
            try {
                Class<?> interfaceClass = Class.forName(spiInterface);
                ExtensionLoader<?> loader = ExtensionLoader.getExtensionLoader(interfaceClass);
                
                // 通过反射清理 Dubbo 内部缓存
                clearExtensionLoaderCache(loader, extensionName, clazz);
                
                log.info("Cleared Dubbo ExtensionLoader cache for: {}", cacheKey);
            } catch (Exception e) {
                log.warn("Failed to clear Dubbo ExtensionLoader cache: {}", cacheKey, e);
            }
            
            // 2. 释放 ClassLoader（核心：断开引用链）
            DynamicClassLoader classLoader = classLoaderMap.remove(className);
            if (classLoader != null) {
                classLoader.unload(); // 清理 ClassLoader 内部的类引用
                log.info("Released ClassLoader for class: {}, ClassLoader: {}", 
                        className, Integer.toHexString(classLoader.hashCode()));
            }
            
            // 3. 清空所有引用
            clazz = null;
            classLoader = null;
            
            // 4. 建议 JVM 进行垃圾回收
            System.gc();
            
            log.info("Successfully removed and unloaded dynamic SPI: {}, className: {}", cacheKey, className);
            log.info("Class will be unloaded when GC collects the ClassLoader");
            
        } else {
            log.warn("Dynamic SPI not found in cache: {}", cacheKey);
        }
    }
    
    /**
     * 通过反射清理 Dubbo ExtensionLoader 的内部缓存
     * 
     * Dubbo ExtensionLoader 内部维护的缓存：
     * - cachedInstances: 扩展实例缓存（ConcurrentMap<String, Holder<Object>>）
     * - cachedClasses: 扩展类缓存（Holder<Map<String, Class<?>>>）
     */
    private void clearExtensionLoaderCache(ExtensionLoader<?> loader, String extensionName, Class<?> clazz) {
        try {
            // 清理 cachedInstances（扩展实例缓存）
            Field cachedInstancesField = ExtensionLoader.class.getDeclaredField("cachedInstances");
            cachedInstancesField.setAccessible(true);
            Object cachedInstances = cachedInstancesField.get(loader);
            if (cachedInstances instanceof ConcurrentHashMap) {
                ((ConcurrentHashMap<?, ?>) cachedInstances).remove(extensionName);
                log.debug("Cleared cachedInstances for extension: {}", extensionName);
            }
            
            // 清理 cachedClasses（扩展类缓存）
            Field cachedClassesField = ExtensionLoader.class.getDeclaredField("cachedClasses");
            cachedClassesField.setAccessible(true);
            Object cachedClasses = cachedClassesField.get(loader);
            if (cachedClasses != null) {
                // cachedClasses 是 Holder<Map<String, Class<?>>> 类型
                Field valueField = cachedClasses.getClass().getDeclaredField("value");
                valueField.setAccessible(true);
                Object classesMap = valueField.get(cachedClasses);
                if (classesMap instanceof Map) {
                    ((Map<?, ?>) classesMap).remove(extensionName);
                    log.debug("Cleared cachedClasses for extension: {}", extensionName);
                }
            }
            
            log.debug("Successfully cleared Dubbo ExtensionLoader internal cache for: {}", extensionName);
            
        } catch (NoSuchFieldException e) {
            log.warn("Dubbo ExtensionLoader structure may have changed, field not found: {}", e.getMessage());
        } catch (IllegalAccessException e) {
            log.warn("Failed to access Dubbo ExtensionLoader private fields: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Unexpected error while clearing Dubbo ExtensionLoader cache", e);
        }
    }

    private String buildCacheKey(String spiInterface, String extensionName) {
        return spiInterface + "#" + extensionName;
    }

    public Map<String, Class<?>> getAllRegisteredExtensions() {
        return new ConcurrentHashMap<>(registeredExtensions);
    }

    public void clear() {
        log.info("Clearing all dynamic SPIs, count: {}", registeredExtensions.size());
        
        // 卸载所有 ClassLoader
        for (DynamicClassLoader loader : classLoaderMap.values()) {
            loader.unload();
        }
        
        registeredExtensions.clear();
        classLoaderMap.clear();
        
        // 触发 GC
        System.gc();
        
        log.info("Cleared all dynamic SPIs and triggered GC");
    }
}
