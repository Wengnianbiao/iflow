package com.helianhealth.agent.common.extension;

import com.helianhealth.agent.common.extension.factory.AgentExtensionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Agent Extension Loader - AgentSPI Dynamic Loader
 * <p>
 * Reference implementation based on Dubbo ExtensionLoader, providing a flexible SPI mechanism
 * for dynamic extension loading and management.
 * <p>
 * <b>Core Features:</b>
 * <ul>
 * <li>Static Loading: Read configuration from META-INF/agent/ directory</li>
 * <li>Dynamic Registration: Add/replace extensions at runtime</li>
 * <li>IOC Injection: Auto-inject SPI dependencies through constructor</li>
 * <li>Adaptive Proxy: Dynamically select extension implementation (supports @AgentAdaptive)</li>
 * <li>Activate Extensions: Auto-activate extensions based on conditions (supports @AgentActivate)</li>
 * </ul>
 * <p>
 * <b>Usage Examples:</b>
 * <pre>{@code
 * // Get specific extension
 * InterfaceRemoteInvoker invoker = AgentExtensionLoader
 *     .getExtensionLoader(InterfaceRemoteInvoker.class)
 *     .getExtension("http");
 *
 * // Get adaptive extension
 * InterfaceRemoteInvoker adaptive = AgentExtensionLoader
 *     .getExtensionLoader(InterfaceRemoteInvoker.class)
 *     .getAdaptiveExtension();
 *
 * // Get activate extension list
 * List<Filter> filters = AgentExtensionLoader
 *     .getExtensionLoader(Filter.class)
 *     .getActivateExtensions(params);
 * }</pre>
 *
 * @param <T> the extension interface type
 */
public class AgentExtensionLoader<T> {

    private static final Logger log = LoggerFactory.getLogger(AgentExtensionLoader.class);

    /**
     * 静态配置文件目录
     */
    private static final String AGENT_DIRECTORY = "META-INF/agent/";

    /**
     * Loader缓存
     * key: 接口类, value: loader实例
     */
    private static final ConcurrentMap<Class<?>, AgentExtensionLoader<?>> EXTENSION_LOADERS =
        new ConcurrentHashMap<>();

    /**
     * 实例缓存
     * key: 扩展名, value: 实例
     */
    private final ConcurrentMap<String, T> cachedInstances = new ConcurrentHashMap<>();

    /**
     * 自适应扩展缓存
     */
    private volatile T cachedAdaptiveInstance;

    /**
     * 自定义自适应扩展类（标注@AgentAdaptive的类）
     */
    private volatile Class<?> cachedAdaptiveClass;

    /**
     * Activate注解缓存
     * key: 扩展名, value: AgentActivate注解
     */
    private final Map<String, AgentActivate> cachedActivates = new ConcurrentHashMap<>();

    /**
     * 扩展接口类型
     */
    private final Class<T> type;

    /**
     * 默认扩展名（从@AgentSPI注解获取）
     */
    private final String defaultExtensionName;

    /**
     * 扩展注册中心引用
     */
    private final ExtensionRegistry registry;

    /**
     * 扩展工厂（用于依赖注入）
     */
    private final AgentExtensionFactory extensionFactory;

    /**
     * 是否已加载静态配置
     */
    private volatile boolean configLoaded = false;

    private AgentExtensionLoader(Class<T> type) {
        this.type = type;
        this.registry = ExtensionRegistry.getInstance();
        this.extensionFactory = (type == AgentExtensionFactory.class ? null :
            AgentExtensionLoader.getExtensionLoader(AgentExtensionFactory.class).getAdaptiveExtension());

        AgentSPI spi = type.getAnnotation(AgentSPI.class);
        this.defaultExtensionName = (spi != null && !spi.value().isEmpty())
            ? spi.value() : null;
    }

    /**
     * 获取扩展加载器
     *
     * @param type 接口类型（必须标注@AgentSPI）
     */
    @SuppressWarnings("unchecked")
    public static <T> AgentExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type (" + type + ") is not interface!");
        }
        if (!type.isAnnotationPresent(AgentSPI.class)) {
            throw new IllegalArgumentException("Extension type (" + type +
                ") is not extension, because without @AgentSPI Annotation!");
        }

        AgentExtensionLoader<T> loader = (AgentExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
            EXTENSION_LOADERS.putIfAbsent(type, new AgentExtensionLoader<>(type));
            loader = (AgentExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }

    /**
     * 获取指定名称的扩展
     *
     * @param name 扩展名称（如 "http", "database"）
     * @return 扩展实例（单例）
     */
    public T getExtension(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name == null");
        }

        // 确保已加载静态配置
        ensureConfigLoaded();

        // 检查缓存
        T instance = cachedInstances.get(name);
        if (instance != null) {
            return instance;
        }

        synchronized (cachedInstances) {
            instance = cachedInstances.get(name);
            if (instance != null) {
                return instance;
            }

            // 从注册中心获取Class
            String key = type.getName() + "#" + name;
            Class<?> clazz = registry.getExtensionClass(key);
            if (clazz == null) {
                throw new IllegalStateException("Extension not found: " + key);
            }

            try {
                // 创建实例并注入依赖
                @SuppressWarnings("unchecked")
                T newInstance = (T) registry.createInstanceWithInjection(clazz, extensionFactory);
                cachedInstances.put(name, newInstance);

                // 缓存Activate注解
                cacheActivateAnnotation(name, clazz);

                return newInstance;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create instance: " + key, e);
            }
        }
    }

    /**
     * 获取默认扩展
     *
     * @return 默认扩展实例，如果没有配置默认值则返回null
     */
    public T getDefaultExtension() {
        if (defaultExtensionName == null || defaultExtensionName.isEmpty()) {
            return null;
        }
        return getExtension(defaultExtensionName);
    }

    /**
     * 获取默认扩展名
     */
    public String getDefaultExtensionName() {
        return defaultExtensionName;
    }

    /**
     * 获取自适应扩展
     * 自适应扩展会根据运行时参数动态选择具体实现
     * 如果存在@AgentAdaptive标注的类，则优先使用
     */
    public T getAdaptiveExtension() {
        if (cachedAdaptiveInstance == null) {
            synchronized (this) {
                if (cachedAdaptiveInstance == null) {
                    cachedAdaptiveInstance = createAdaptiveExtension();
                }
            }
        }
        return cachedAdaptiveInstance;
    }

    /**
     * 获取激活扩展列表
     *
     * @param params 上下文参数，用于匹配@AgentActivate的value条件
     * @return 按order排序的扩展列表
     */
    public List<T> getActivateExtensions(Map<String, String> params) {
        ensureConfigLoaded();

        List<ActivateInfo<T>> activateInfos = new ArrayList<>();

        // 遍历所有已注册的扩展
        for (String name : getSupportedExtensions()) {
            T extension = getExtension(name);
            AgentActivate activate = cachedActivates.get(name);

            if (activate == null) {
                continue;
            }

            // 检查value条件
            if (!isActive(activate, params)) {
                continue;
            }

            activateInfos.add(new ActivateInfo<>(name, extension, activate));
        }

        // 排序
        activateInfos.sort(ActivateInfo::compareTo);

        // 提取结果
        List<T> result = new ArrayList<>(activateInfos.size());
        for (ActivateInfo<T> info : activateInfos) {
            result.add(info.extension);
        }

        return result;
    }

    /**
     * 获取所有激活扩展（无参数条件）
     */
    public List<T> getActivateExtensions() {
        return getActivateExtensions(Collections.emptyMap());
    }

    /**
     * 添加扩展
     *
     * @param name 扩展名称
     * @param clazz 实现类
     */
    public void addExtension(String name, Class<?> clazz) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name == null");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("Extension class == null");
        }
        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class " + clazz.getName() +
                " does not implement " + type.getName());
        }

        // 检查是否是@AgentAdaptive标注的类
        if (clazz.isAnnotationPresent(AgentAdaptive.class)) {
            if (cachedAdaptiveClass == null) {
                cachedAdaptiveClass = clazz;
            } else if (!cachedAdaptiveClass.equals(clazz)) {
                throw new IllegalStateException("More than 1 adaptive class found: " +
                    cachedAdaptiveClass.getName() + ", " + clazz.getName());
            }
            log.info("Set adaptive class: {} for {}", clazz.getName(), type.getName());
            return;
        }

        // 检查是否已存在
        if (hasExtension(name)) {
            throw new IllegalStateException("Extension name " + name +
                " already exists for " + type.getName());
        }

        registry.registerExtension(type.getName(), name, clazz);

        // 缓存Activate注解
        cacheActivateAnnotation(name, clazz);

        // 清除缓存
        cachedInstances.remove(name);

        log.info("Added extension: {}#{}", type.getName(), name);
    }

    /**
     * 替换扩展
     *
     * @param name 扩展名称
     * @param clazz 新实现类
     */
    public void replaceExtension(String name, Class<?> clazz) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name == null");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("Extension class == null");
        }
        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class " + clazz.getName() +
                " does not implement " + type.getName());
        }

        // 检查是否存在
        if (!hasExtension(name)) {
            throw new IllegalStateException("Extension name " + name +
                " not found for " + type.getName());
        }

        registry.replaceExtension(type.getName(), name, clazz);

        // 更新Activate注解缓存
        cacheActivateAnnotation(name, clazz);

        // 清除缓存
        cachedInstances.remove(name);
        cachedAdaptiveInstance = null;

        log.info("Replaced extension: {}#{}", type.getName(), name);
    }

    /**
     * 移除扩展
     */
    public void removeExtension(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name == null");
        }

        registry.removeExtension(type.getName(), name);
        cachedInstances.remove(name);
        cachedActivates.remove(name);
        cachedAdaptiveInstance = null;

        log.info("Removed extension: {}#{}", type.getName(), name);
    }

    // ==================== 动态扩展管理（支持运行时编译和类卸载） ====================

    /**
     * 注册动态扩展（支持源码编译或从类路径加载）
     *
     * @param name 扩展名称
     * @param className 类全限定名
     * @param sourceCode 源码（可选，为null时从类路径加载）
     */
    public void registerDynamicExtension(String name, String className, String sourceCode) throws Exception {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name == null");
        }
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Class name == null");
        }

        log.debug("Registering dynamic extension: interface={}, name={}, class={}",
                type.getName(), name, className);

        // 委托ExtensionRegistry进行加载和注册（支持动态编译）
        registry.loadAndRegister(type.getName(), name, className, sourceCode);

        // 清除缓存，确保下次getExtension时重新加载
        cachedInstances.remove(name);
        cachedAdaptiveInstance = null;

        // 尝试缓存Activate注解（如果类已加载）
        try {
            Class<?> clazz = Class.forName(className);
            cacheActivateAnnotation(name, clazz);
        } catch (ClassNotFoundException e) {
            // 动态编译的类可能无法通过Class.forName获取，忽略
            log.debug("Class not in classpath (expected for dynamic compiled): {}", className);
        }

        log.info("Registered dynamic extension: {}#{}", type.getName(), name);
    }

    /**
     * 移除动态扩展（支持类卸载）
     *
     * @param name 扩展名称
     */
    public void removeDynamicExtension(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name == null");
        }

        // 清理所有缓存
        cachedInstances.remove(name);
        cachedActivates.remove(name);
        cachedAdaptiveInstance = null;

        // 委托ExtensionRegistry进行类卸载
        registry.removeExtension(type.getName(), name);

        log.info("Removed dynamic extension with class unloading: {}#{}", type.getName(), name);
    }

    /**
     * 刷新动态扩展（卸载后重新加载）
     *
     * @param name 扩展名称
     * @param className 类全限定名
     * @param sourceCode 源码（可选）
     */
    public void refreshDynamicExtension(String name, String className, String sourceCode) throws Exception {
        log.info("Refreshing dynamic extension: {}#{}", type.getName(), name);

        // 先卸载
        removeDynamicExtension(name);

        // 重新注册
        registerDynamicExtension(name, className, sourceCode);

        log.info("Successfully refreshed dynamic extension: {}#{}", type.getName(), name);
    }

    /**
     * 检查扩展是否存在
     */
    public boolean hasExtension(String name) {
        ensureConfigLoaded();
        return registry.hasExtension(type.getName(), name);
    }

    /**
     * 获取所有已注册的扩展名
     */
    public Set<String> getSupportedExtensions() {
        ensureConfigLoaded();
        return registry.getExtensionNames(type.getName());
    }

    /**
     * 清除缓存（用于测试或热加载）
     */
    public void clearCache() {
        cachedInstances.clear();
        cachedActivates.clear();
        cachedAdaptiveInstance = null;
        cachedAdaptiveClass = null;
    }

    /**
     * 创建自适应扩展
     */
    @SuppressWarnings("unchecked")
    private T createAdaptiveExtension() {
        ensureConfigLoaded();
        // 优先使用自定义的@AgentAdaptive类
        if (cachedAdaptiveClass != null) {
            try {
                return (T) registry.createInstanceWithInjection(cachedAdaptiveClass, extensionFactory);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create adaptive instance: " +
                    cachedAdaptiveClass.getName(), e);
            }
        }

        // 否则使用动态代理,这里是和dubbo不一样的地方,dubbo是动态编译自适应拓展类
        // agent则是使用动态代理来创建自适应扩展(有反射开销)
        return AdaptiveExtensionProxy.createProxy(type, this);
    }

    /**
     * 确保静态配置已加载
     */
    private void ensureConfigLoaded() {
        if (!configLoaded) {
            synchronized (this) {
                if (!configLoaded) {
                    loadExtensionsFromConfig();
                    configLoaded = true;
                }
            }
        }
    }

    /**
     * 从配置文件加载扩展
     */
    private void loadExtensionsFromConfig() {
        String fileName = AGENT_DIRECTORY + type.getName();
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> urls = classLoader.getResources(fileName);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                loadResource(url);
            }
        } catch (Exception e) {
            log.error("Failed to load extension config for {}", type.getName(), e);
        }
    }

    /**
     * 加载单个配置文件
     */
    private void loadResource(URL resourceURL) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resourceURL.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 跳过注释
                int ci = line.indexOf('#');
                if (ci >= 0) {
                    line = line.substring(0, ci);
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // 解析 name=className 格式
                int i = line.indexOf('=');
                if (i > 0) {
                    String extensionName = line.substring(0, i).trim();
                    String className = line.substring(i + 1).trim();

                    if (!className.isEmpty()) {
                        try {
                            loadClass(extensionName, className, resourceURL);
                        } catch (Exception e) {
                            log.error("Failed to load extension: name={}, class={}, from={}",
                                extensionName, className, resourceURL, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to read extension config: {}", resourceURL, e);
        }
    }

    /**
     * 加载扩展类并进行验证
     */
    private void loadClass(String name, String className, URL resourceURL) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(className, true,
            Thread.currentThread().getContextClassLoader());

        // 验证：确保加载的类是接口的实现类
        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException(
                String.format("Error when load extension class (interface: %s, class line: %s), " +
                    "class %s is not subtype of interface.", type, className, className));
        }

        // 检查是否是@AgentAdaptive标注的类
        if (clazz.isAnnotationPresent(AgentAdaptive.class)) {
            if (cachedAdaptiveClass == null) {
                cachedAdaptiveClass = clazz;
                log.info("Found adaptive extension class: {} for {}", clazz.getName(), type.getName());
            } else if (!cachedAdaptiveClass.equals(clazz)) {
                throw new IllegalStateException(
                    String.format("More than 1 adaptive class found: %s, %s",
                        cachedAdaptiveClass.getName(), clazz.getName()));
            }
            // @AgentAdaptive类不注册到registry
            return;
        }

        // TODO: 支持Wrapper类（包装器模式）
        // if (isWrapperClass(clazz)) {
        //     Set<Class<?>> wrappers = cachedWrapperClasses;
        //     if (wrappers == null) {
        //         cachedWrapperClasses = new ConcurrentHashSet<>();
        //         wrappers = cachedWrapperClasses;
        //     }
        //     wrappers.add(clazz);
        //     return;
        // }

        // 普通扩展类处理
        if (name == null || name.isEmpty()) {
            throw new IllegalStateException(
                String.format("No extension name for the class %s in the config %s",
                    className, resourceURL));
        }

        // 注册普通扩展
        registry.registerExtension(type.getName(), name, clazz);

        // 缓存Activate注解
        cacheActivateAnnotation(name, clazz);
    }

    /**
     * 缓存Activate注解
     */
    private void cacheActivateAnnotation(String name, Class<?> clazz) {
        AgentActivate activate = clazz.getAnnotation(AgentActivate.class);
        if (activate != null) {
            cachedActivates.put(name, activate);
        }
    }

    /**
     * 检查value条件是否满足
     */
    private boolean isActive(AgentActivate activate, Map<String, String> params) {
        String[] keys = activate.value();
        if (keys == null || keys.length == 0) {
            return true;  // value为空，总是激活
        }
        // value不为空时才检查params
        if (params == null || params.isEmpty()) {
            return false;
        }
        for (String key : keys) {
            String value = params.get(key);
            if (value != null && !value.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public Class<T> getType() {
        return type;
    }

    /**
     * Activate信息封装，用于排序
     */
    private static class ActivateInfo<T> implements Comparable<ActivateInfo<T>> {
        final String name;
        final T extension;
        final AgentActivate activate;

        ActivateInfo(String name, T extension, AgentActivate activate) {
            this.name = name;
            this.extension = extension;
            this.activate = activate;
        }

        @Override
        public int compareTo(ActivateInfo<T> other) {
            return Integer.compare(this.activate.order(), other.activate.order());
        }
    }
}
