package com.helianhealth.agent.extension.spi.dynamic;

import com.helianhealth.agent.extension.spi.model.DynamicSpiDefinition;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic SPI Manager
 * Uses isolated ClassLoader architecture to support complete class unloading
 * Core Design:
 * 1. Each dynamic class uses an independent DynamicClassLoader instance
 * 2. ClassLoader and class are bound one-to-one, living and dying together
 * 3. Release ClassLoader during unloading to trigger JVM class unloading mechanism
 */
public class DynamicSpiManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicSpiManager.class);

    /**
     * Registered dynamic extension cache
     * key: spiInterface#extensionName, value: Class object
     */
    private final Map<String, Class<?>> registeredExtensions = new ConcurrentHashMap<>();

    /**
     * ClassLoader cache (one-to-one binding with class)
     * key: className, value: DynamicClassLoader
     */
    private final Map<String, DynamicClassLoader> classLoaderMap = new ConcurrentHashMap<>();

    /**
     * Parent ClassLoader (used to create isolated ClassLoader)
     */
    private final ClassLoader parentClassLoader;

    public DynamicSpiManager() {
        this.parentClassLoader = Thread.currentThread().getContextClassLoader();
    }

    /**
     * Load and register dynamic SPI extension
     * <p>
     * Supports two modes:
     * 1. Dynamic compilation mode: sourceCode is not empty, compile from string
     * 2. Project mode: sourceCode is empty, load pre-compiled class from classpath
     *
     * @param dynamicSpi SPI definition
     */
    public void loadAndRegister(DynamicSpiDefinition dynamicSpi) throws Exception {
        String className = dynamicSpi.getClassName();
        String sourceCode = dynamicSpi.getSourceCode();
        String spiInterface = dynamicSpi.getSpiInterface();
        String extensionName = dynamicSpi.getExtensionName();

        log.debug("Loading dynamic SPI: interface={}, name={}, class={}",
                spiInterface, extensionName, className);

        // Create independent ClassLoader for each class
        DynamicClassLoader classLoader = new DynamicClassLoader(parentClassLoader, className);

        // Decide loading mode based on whether sourceCode is empty
        Class<?> clazz;
        if (sourceCode != null && !sourceCode.trim().isEmpty()) {
            // Mode 1: Dynamic compilation mode
            log.debug("Using dynamic compilation mode: {}", className);
            clazz = classLoader.compileAndLoad(sourceCode);
        } else {
            // Mode 2: Project mode (load from classpath)
            log.debug("Loading from classpath: {}", className);
            clazz = classLoader.loadFromClasspath();
        }

        // Cache ClassLoader (bind with class)
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

        log.info("Registered dynamic SPI: {}", cacheKey);
    }

    /**
     * Batch load SPI extensions (workflow layer ensures only enabled configs are passed)
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
     * Refresh SPI extension (unload and reload)
     */
    public void refresh(DynamicSpiDefinition dynamicSpi) throws Exception {
        // Unload old one first
        remove(dynamicSpi.getSpiInterface(), dynamicSpi.getExtensionName());

        // Reload
        loadAndRegister(dynamicSpi);

        String cacheKey = buildCacheKey(dynamicSpi.getSpiInterface(), dynamicSpi.getExtensionName());
        log.info("Successfully refreshed dynamic SPI: {}", cacheKey);
    }

    /**
     * Remove dynamic SPI extension and completely unload class
     *
     * Unloading steps:
     * 1. Remove from registeredExtensions cache
     * 2. Clear Dubbo ExtensionLoader internal cache
     * 3. Release DynamicClassLoader (break all references)
     * 4. Trigger GC to let JVM unload class
     *
     * @param spiInterface Interface full qualified name
     * @param extensionName Extension name
     */
    public void remove(String spiInterface, String extensionName) {
        String cacheKey = buildCacheKey(spiInterface, extensionName);
        Class<?> clazz = registeredExtensions.remove(cacheKey);

        if (clazz != null) {
            String className = clazz.getName();

            // 1. Remove extension from Dubbo ExtensionLoader
            try {
                Class<?> interfaceClass = Class.forName(spiInterface);
                ExtensionLoader<?> loader = ExtensionLoader.getExtensionLoader(interfaceClass);

                // Clear Dubbo internal cache via reflection
                clearExtensionLoaderCache(loader, extensionName, clazz);

                log.debug("Cleared Dubbo cache for: {}", cacheKey);
            } catch (Exception e) {
                log.warn("Failed to clear Dubbo ExtensionLoader cache: {}", cacheKey, e);
            }

            // 2. Release ClassLoader (core: break reference chain)
            DynamicClassLoader classLoader = classLoaderMap.remove(className);
            if (classLoader != null) {
                classLoader.unload(); // Clear class reference inside ClassLoader
                log.debug("Released ClassLoader: {}@{}",
                        classLoader.getClass().getSimpleName(), Integer.toHexString(classLoader.hashCode()));
            }

            // 3. Clear all references
            clazz = null;
            classLoader = null;

            // 4. Suggest JVM to perform garbage collection
            System.gc();

            log.info("Removed dynamic SPI: {}", cacheKey);

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
            // Clear cachedInstances (extension instance cache)
            Field cachedInstancesField = ExtensionLoader.class.getDeclaredField("cachedInstances");
            cachedInstancesField.setAccessible(true);
            Object cachedInstances = cachedInstancesField.get(loader);
            if (cachedInstances instanceof ConcurrentHashMap) {
                ((ConcurrentHashMap<?, ?>) cachedInstances).remove(extensionName);
                log.debug("Cleared cachedInstances for extension: {}", extensionName);
            }

            // Clear cachedClasses (extension class cache)
            Field cachedClassesField = ExtensionLoader.class.getDeclaredField("cachedClasses");
            cachedClassesField.setAccessible(true);
            Object cachedClasses = cachedClassesField.get(loader);
            if (cachedClasses != null) {
                // cachedClasses is Holder<Map<String, Class<?>>> type
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
        int count = registeredExtensions.size();
        if (count > 0) {
            log.info("Clearing {} dynamic SPIs", count);
        }

        // Unload all ClassLoaders
        for (DynamicClassLoader loader : classLoaderMap.values()) {
            loader.unload();
        }

        registeredExtensions.clear();
        classLoaderMap.clear();

        // Trigger GC
        System.gc();
    }
}
