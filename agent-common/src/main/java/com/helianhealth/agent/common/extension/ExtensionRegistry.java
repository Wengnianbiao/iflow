package com.helianhealth.agent.common.extension;

import com.helianhealth.agent.common.extension.compiler.DynamicCompiler;
import com.helianhealth.agent.common.extension.factory.AgentExtensionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent Extension Registry
 *
 * 扩展注册中心，管理接口到实现的映射关系
 *
 * 功能特性：
 * 1. 扩展注册：注册/移除/替换扩展
 * 2. 扩展获取：获取扩展实例（单例或新实例）
 * 3. 动态编译：支持从源码字符串编译加载
 * 4. IOC注入：支持有参构造器自动注入SPI依赖
 *
 * Key格式：interfaceName#extensionName
 *
 * @author Agent Team
 */
public class ExtensionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ExtensionRegistry.class);

    private static final ExtensionRegistry INSTANCE = new ExtensionRegistry();

    /**
     * Extension class cache
     * key: interfaceName#extensionName
     * value: extension class
     */
    private final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();

    /**
     * Extension instance cache
     * key: interfaceName#extensionName
     * value: extension instance
     */
    private final Map<String, Object> instanceCache = new ConcurrentHashMap<>();

    /**
     * ClassLoader cache for dynamic compiled classes
     * key: className
     * value: DynamicClassLoader
     */
    private final Map<String, DynamicCompiler.DynamicClassLoader> classLoaderCache = new ConcurrentHashMap<>();

    private final ClassLoader parentClassLoader;

    private ExtensionRegistry() {
        this.parentClassLoader = Thread.currentThread().getContextClassLoader();
    }

    public static ExtensionRegistry getInstance() {
        return INSTANCE;
    }

    // ==================== 动态注册 ====================

    /**
     * 加载并注册扩展（支持动态编译和类路径加载）
     *
     * @param interfaceName 接口全限定名
     * @param extensionName 扩展名称
     * @param className 实现类全限定名
     * @param sourceCode 源代码（为空则从类路径加载）
     */
    public void loadAndRegister(String interfaceName, String extensionName,
                                String className, String sourceCode) throws Exception {
        validateParams(interfaceName, extensionName, className);

        log.debug("Loading extension: interface={}, name={}, class={}",
                interfaceName, extensionName, className);

        Class<?> clazz;

        if (sourceCode != null && !sourceCode.trim().isEmpty()) {
            log.debug("Dynamic compilation mode: {}", className);
            clazz = compileClass(className, sourceCode);
        } else {
            log.debug("Loading from classpath: {}", className);
            clazz = Class.forName(className, true, parentClassLoader);
        }
        // 注册拓展类
        registerExtension(interfaceName, extensionName, clazz);
    }

    /**
     * 注册扩展类
     */
    public void registerExtension(String interfaceName, String extensionName, Class<?> clazz) {
        validateParams(interfaceName, extensionName, clazz.getName());

        String key = buildKey(interfaceName, extensionName);

        // 验证实现关系
        try {
            Class<?> interfaceClass = Class.forName(interfaceName);
            if (!interfaceClass.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException(
                    String.format("Class %s does not implement interface %s",
                        clazz.getName(), interfaceName));
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Interface class not found: " + interfaceName, e);
        }

        // 检查是否是@AgentAdaptive标注的类
        boolean isAdaptive = clazz.isAnnotationPresent(AgentAdaptive.class);

        if (isAdaptive) {
            log.info("Found adaptive extension class: {} for {}", clazz.getName(), interfaceName);
            // 标记为 adaptive class，返回给调用方（AgentExtensionLoader）缓存
            return;
        }

        classCache.put(key, clazz);
        instanceCache.remove(key);

        log.info("Extension registered: {}", key);
    }

    /**
     * 替换已有扩展
     */
    public void replaceExtension(String interfaceName, String extensionName, Class<?> clazz) {
        String key = buildKey(interfaceName, extensionName);

        if (!classCache.containsKey(key)) {
            throw new IllegalStateException("Extension not found for replacement: " + key);
        }

        // 先移除旧的
        removeExtension(interfaceName, extensionName);

        // 再注册新的
        registerExtension(interfaceName, extensionName, clazz);

        log.info("Extension replaced: {}", key);
    }

    /**
     * 移除扩展并释放ClassLoader
     */
    public void removeExtension(String interfaceName, String extensionName) {
        String key = buildKey(interfaceName, extensionName);
        Class<?> clazz = classCache.remove(key);
        instanceCache.remove(key);

        if (clazz != null) {
            String className = clazz.getName();
            DynamicCompiler.DynamicClassLoader classLoader = classLoaderCache.remove(className);
            if (classLoader != null) {
                classLoader.unload();
            }
            System.gc();
        }

        log.info("Extension removed: {}", key);
    }

    // ==================== 获取扩展 ====================

    /**
     * 获取扩展类（不创建实例）
     *
     * @param key interfaceName#extensionName
     * @return 扩展类，如果不存在则返回null
     */
    public Class<?> getExtensionClass(String key) {
        return classCache.get(key);
    }

    /**
     * 获取扩展实例（单例，带IOC注入）
     *
     * @deprecated 建议使用 getExtensionClass + createInstanceWithInjection
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public <T> T getExtension(String interfaceName, String extensionName) {
        String key = buildKey(interfaceName, extensionName);

        Object instance = instanceCache.get(key);
        if (instance != null) {
            return (T) instance;
        }

        synchronized (this) {
            instance = instanceCache.get(key);
            if (instance != null) {
                return (T) instance;
            }

            Class<?> clazz = classCache.get(key);
            if (clazz == null) {
                throw new IllegalStateException("Extension not found: " + key);
            }

            try {
                instance = createInstanceWithInjection(clazz, null);
                instanceCache.put(key, instance);
                log.debug("Extension instance created: {}", key);
                return (T) instance;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create instance: " + key, e);
            }
        }
    }

    /**
     * 获取新实例（非单例，带IOC注入）
     */
    @SuppressWarnings("unchecked")
    public <T> T getNewExtension(String interfaceName, String extensionName) {
        String key = buildKey(interfaceName, extensionName);
        Class<?> clazz = classCache.get(key);
        if (clazz == null) {
            throw new IllegalStateException("Extension not found: " + key);
        }

        try {
            return (T) createInstanceWithInjection(clazz, null);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create instance: " + key, e);
        }
    }

    /**
     * 检查扩展是否存在
     */
    public boolean hasExtension(String interfaceName, String extensionName) {
        String key = buildKey(interfaceName, extensionName);
        return classCache.containsKey(key);
    }

    /**
     * 获取所有已注册的扩展
     */
    public Map<String, Class<?>> getAllExtensions() {
        return new ConcurrentHashMap<>(classCache);
    }

    /**
     * 获取指定接口的所有扩展名
     */
    public Set<String> getExtensionNames(String interfaceName) {
        String prefix = interfaceName + "#";
        Set<String> names = new HashSet<>();
        for (String key : classCache.keySet()) {
            if (key.startsWith(prefix)) {
                names.add(key.substring(prefix.length()));
            }
        }
        return names;
    }

    /**
     * 清空所有扩展
     */
    public void clear() {
        int count = classCache.size();

        for (DynamicCompiler.DynamicClassLoader loader : classLoaderCache.values()) {
            loader.unload();
        }

        classCache.clear();
        instanceCache.clear();
        classLoaderCache.clear();

        if (count > 0) {
            log.info("Cleared {} extensions", count);
        }
    }

    // ==================== IOC注入 ====================

    /**
     * 创建实例并注入依赖
     * <p>
     * 注入策略：
     * 1. 构造器注入：优先尝试参数最多的构造器，通过 ExtensionFactory 获取依赖
     * 2. 无参构造器：所有带参构造器都失败时才尝试（不强制要求）
     * 3. Setter注入：创建实例后，通过 ExtensionFactory 获取依赖并调用 setter 方法
     * </p>
     *
     * @param clazz 扩展类
     * @param extensionFactory 扩展工厂（用于依赖注入）
     */
    public Object createInstanceWithInjection(Class<?> clazz, AgentExtensionFactory extensionFactory) throws Exception {
        Constructor<?>[] constructors = clazz.getConstructors();

        // 按参数数量降序排序，优先使用参数最多的构造器
        Arrays.sort(constructors, (a, b) ->
            b.getParameterTypes().length - a.getParameterTypes().length);

        Object instance = null;
        Constructor<?> noArgConstructor = null;

        // 尝试构造器注入
        for (Constructor<?> ctor : constructors) {
            Class<?>[] paramTypes = ctor.getParameterTypes();

            // 记录无参构造器，稍后降级使用
            if (paramTypes.length == 0) {
                noArgConstructor = ctor;
                continue;
            }

            // 如果没有 extensionFactory，无法注入依赖，跳过带参构造器
            if (extensionFactory == null) {
                continue;
            }

            // 尝试通过 ExtensionFactory 获取所有参数的依赖
            boolean allInjectable = true;
            Object[] args = new Object[paramTypes.length];

            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> paramType = paramTypes[i];

                // 通过 ExtensionFactory 获取依赖（支持 SPI、Spring Bean 等多种来源）
                Object dependency = extensionFactory.getExtension(paramType, paramType.getName());
                if (dependency != null) {
                    args[i] = dependency;
                } else {
                    allInjectable = false;
                    log.debug("Cannot inject parameter {} for constructor of {}",
                        paramType.getName(), clazz.getSimpleName());
                    break;
                }
            }

            if (allInjectable) {
                log.debug("Creating instance with constructor injection: {} ({} params)",
                        clazz.getSimpleName(), paramTypes.length);
                instance = ctor.newInstance(args);
                break;
            }
        }

        // 如果构造器注入失败，尝试无参构造器（不强制要求）
        if (instance == null) {
            if (noArgConstructor != null) {
                log.debug("Creating instance with no-arg constructor: {}", clazz.getSimpleName());
                instance = noArgConstructor.newInstance();
            } else {
                throw new IllegalStateException(
                    String.format("No suitable constructor found for %s. " +
                        "Either provide a no-arg constructor or ensure all dependencies can be injected.",
                        clazz.getName()));
            }
        }

        // Setter注入（作为补充）
        if (extensionFactory != null) {
            injectViaSetter(instance, clazz, extensionFactory);
        }

        return instance;
    }

    /**
     * 通过Setter注入依赖
     */
    private void injectViaSetter(Object instance, Class<?> clazz, AgentExtensionFactory extensionFactory) throws Exception {
        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            Class<?> fieldType = field.getType();

            // 构造setter方法名
            String setterName = "set" + Character.toUpperCase(field.getName().charAt(0))
                              + field.getName().substring(1);

            try {
                java.lang.reflect.Method setter = clazz.getMethod(setterName, fieldType);

                // 通过ExtensionFactory获取依赖
                Object dependency = extensionFactory.getExtension(fieldType, field.getName());
                if (dependency != null) {
                    setter.invoke(instance, dependency);
                    log.debug("Injected via setter: {}.{}({})",
                            clazz.getSimpleName(), setterName, fieldType.getSimpleName());
                }
            } catch (NoSuchMethodException e) {
                // Setter不存在，跳过
            }
        }
    }

    // ==================== 动态编译 ====================

    /**
     * 编译Java源码
     */
    private Class<?> compileClass(String className, String sourceCode) throws Exception {
        DynamicCompiler.CompileResult result = DynamicCompiler.getInstance().compile(className, sourceCode);

        // 缓存类加载器以支持后续卸载
        classLoaderCache.put(className, result.getClassLoader());

        log.info("Compiled and loaded: {}", className);
        return result.getClazz();
    }

    // ==================== 工具方法 ====================

    private String buildKey(String interfaceName, String extensionName) {
        return interfaceName + "#" + extensionName;
    }

    private void validateParams(String interfaceName, String extensionName, String className) {
        if (interfaceName == null || interfaceName.isEmpty()) {
            throw new IllegalArgumentException("Interface name cannot be null or empty");
        }
        if (extensionName == null || extensionName.isEmpty()) {
            throw new IllegalArgumentException("Extension name cannot be null or empty");
        }
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Class name cannot be null or empty");
        }
    }

}
