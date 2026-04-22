package com.helianhealth.agent.common.extension;

import com.helianhealth.agent.common.extension.factory.AdaptiveExtensionProxyFactory;
import com.helianhealth.agent.common.extension.registry.ExtensionRegistry;
import com.helianhealth.agent.common.utils.Holder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
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
        new ConcurrentHashMap<>(64);

    // ==============================

    /**
     * 扩展Class缓存
     * value: key为扩展名, value为实现类的映射表
     */
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();

    /**
     * 实例缓存,每个SPI拓展接口缓存的实例
     * key: 扩展名, value: Holder容器
     */
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();

    /**
     * 自适应扩展缓存
     */
    private final Holder<T> cachedAdaptiveInstance = new Holder<>();

    /**
     * 自定义自适应扩展类（标注@AgentAdaptive的类）
     */
    private volatile Class<?> cachedAdaptiveClass;

    /**
     * Wrapper类缓存（构造器参数为SPI接口类型的类）
     */
    private volatile Set<Class<?>> cachedWrapperClasses;

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
     * 默认扩展名
     */
    private String defaultExtensionName;

    /**
     * 扩展注册中心引用
     */
    private final ExtensionRegistry registry;

    /**
     * 扩展工厂（用于依赖注入）
     */
    private final AgentExtensionFactory extensionFactory;

    private AgentExtensionLoader(Class<T> type) {
        this.type = type;
        this.registry = ExtensionRegistry.getInstance();
        this.extensionFactory = (type == AgentExtensionFactory.class ? null :
            AgentExtensionLoader.getExtensionLoader(AgentExtensionFactory.class).getAdaptiveExtension());
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

        // 这里有一个jdk8的bug，ConcurrentHashMap的computeIfAbsent会存在deadlock问题
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
    @SuppressWarnings("unchecked")
    public T getExtension(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name == null");
        }

        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<>());
            holder = cachedInstances.get(name);
        }
        Object instance = holder.get();
        if (instance == null) {
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) {
                    instance = createExtension(name);
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }


    @SuppressWarnings("unchecked")
    private T createExtension(String name) {
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            throw new IllegalStateException("Extension not found: " + type.getName() + "#" + name);
        }

        try {
            T instance = (T) registry.createInstanceWithInjection(clazz, extensionFactory);

            // 自动包装：依次用Wrapper类包装原始实例
            Set<Class<?>> wrapperClasses = cachedWrapperClasses;
            if (wrapperClasses != null && !wrapperClasses.isEmpty()) {
                for (Class<?> wrapperClass : wrapperClasses) {
                    instance = (T) registry.createInstanceWithInjection(wrapperClass, extensionFactory, instance);
                }
            }

            cacheActivateAnnotation(name, clazz);
            return instance;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create instance: " + type.getName() + "#" + name, e);
        }
    }

    /**
     * 获取所有已加载的扩展Class
     * 首次调用时从配置文件全量加载所有扩展类，后续直接返回缓存
     */
    private Map<String, Class<?>> getExtensionClasses() {
        Map<String, Class<?>> classes = cachedClasses.get();
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    classes = loadExtensionClasses();
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }

    /**
     * 全量加载当前 type 的扩展类配置
     */
    private Map<String, Class<?>> loadExtensionClasses() {
        AgentSPI spi = type.getAnnotation(AgentSPI.class);
        if (spi != null && !spi.value().isEmpty()) {
            this.defaultExtensionName = spi.value();
        }
        Map<String, Class<?>> extensionClasses = new ConcurrentHashMap<>();
        loadDirectory(extensionClasses);

        return extensionClasses;
    }

    private void loadDirectory(Map<String, Class<?>> classes) {
        String fileName = AgentExtensionLoader.AGENT_DIRECTORY + type.getName();
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> urls = classLoader.getResources(fileName);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                loadResource(url, classes);
            }
        } catch (Exception e) {
            log.error("Failed to load extension config for {}", type.getName(), e);
        }
    }

    /**
     * 缓存静态扩展Class（从配置文件或编程式注册）
     */
    private void putStaticClass(String name, Class<?> clazz, Map<String, Class<?>> classes) {
        classes.put(name, clazz);
    }

    /**
     * 缓存动态扩展Class（从源码编译或运行时类路径加载，有独立ClassLoader生命周期）
     */
    private void putDynamicClass(String name, Class<?> clazz) {
        getExtensionClasses().put(name, clazz);
    }

    /**
     * 移除扩展Class并尝试卸载动态ClassLoader
     */
    private void removeClass(String name) {
        Class<?> removedClass = getExtensionClasses().remove(name);
        if (removedClass != null) {
            registry.unloadDynamicClass(removedClass.getName());
        }
    }

    /**
     * 获取默认扩展
     *
     * @return 默认扩展实例，如果没有配置默认值则返回null
     */
    public T getDefaultExtension() {
        getExtensionClasses();
        if (defaultExtensionName == null || defaultExtensionName.isEmpty()) {
            return null;
        }
        return getExtension(defaultExtensionName);
    }

    /**
     * 获取默认扩展名
     */
    public String getDefaultExtensionName() {
        getExtensionClasses();
        return defaultExtensionName;
    }

    /**
     * 获取自适应扩展
     * 自适应扩展会根据运行时参数动态选择具体实现
     * 如果存在@AgentAdaptive标注的类，则优先使用
     */
    public T getAdaptiveExtension() {
        T instance = cachedAdaptiveInstance.get();
        if (instance == null) {
            synchronized (cachedAdaptiveInstance) {
                instance = cachedAdaptiveInstance.get();
                if (instance == null) {
                    instance = createAdaptiveExtension();
                    cachedAdaptiveInstance.set(instance);
                }
            }
        }
        return instance;
    }

    /**
     * 获取激活扩展列表
     *
     * @param params 上下文参数，用于匹配@AgentActivate的value条件
     * @return 按order排序的扩展列表
     */
    public List<T> getActivateExtensions(Map<String, String> params) {
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

        activateInfos.sort(ActivateInfo::compareTo);
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

        getExtensionClasses().put(name, clazz);

        // 缓存Activate注解
        cacheActivateAnnotation(name, clazz);

        // 清除缓存
        cachedInstances.remove(name);
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

        // 检查是否存在（触发懒加载）
        if (!hasExtension(name)) {
            throw new IllegalStateException("Extension name " + name +
                " not found for " + type.getName());
        }

        getExtensionClasses().put(name, clazz);

        // 更新Activate注解缓存
        cacheActivateAnnotation(name, clazz);

        // 清除缓存
        cachedInstances.remove(name);
        cachedAdaptiveInstance.set(null);
    }

    /**
     * 移除扩展
     */
    public void removeExtension(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name == null");
        }

        removeClass(name);
        cachedInstances.remove(name);
        cachedActivates.remove(name);
        cachedAdaptiveInstance.set(null);

        log.info("Removed extension: {}#{}", type.getName(), name);
    }

    // ==================== 动态扩展管理（支持运行时编译和类卸载） ====================

    /**
     * 注册动态扩展（支持源码编译或从类路径加载）
     *
     * @param name 扩展名称
     * @param className 类全限定名
     * @param sourceCode 源码（非动态编译模式下可为null）
     * @param dynamicCompile true表示动态编译（独立ClassLoader），false表示类路径加载
     */
    public void registerDynamicExtension(String name, String className, String sourceCode, boolean dynamicCompile) throws Exception {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name == null");
        }
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Class name == null");
        }

        // 扩展名唯一性校验，防止重复注册覆盖已有扩展
        if (hasExtension(name)) {
            throw new IllegalStateException(
                String.format("Dynamic extension name '%s' already exists for %s", name, type.getName()));
        }

        log.info("Registering dynamic extension: interface={}, name={}, class={}",
                type.getName(), name, className);

        // 委托Registry进行动态编译/类路径加载
        Class<?> clazz = registry.loadClass(className, sourceCode, dynamicCompile);

        // 校验扩展类必须是SPI接口的实现类，与静态加载路径保持一致的fail-fast语义
        // 校验失败时需要卸载已加载的动态ClassLoader，避免类泄漏
        if (!type.isAssignableFrom(clazz)) {
            registry.unloadDynamicClass(className);
            throw new IllegalStateException(
                String.format("Dynamic extension class %s is not subtype of interface %s",
                    clazz.getName(), type.getName()));
        }

        putDynamicClass(name, clazz);

        // 清除实例缓存，确保下次getExtension时重新创建
        cachedInstances.remove(name);
        cachedAdaptiveInstance.set(null);

        cacheActivateAnnotation(name, clazz);

        log.info("Registered dynamic extension success: {}#{}", type.getName(), name);
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

        removeClass(name);
        cachedInstances.remove(name);
        cachedActivates.remove(name);
        cachedAdaptiveInstance.set(null);

        log.info("Removed dynamic extension with class unloading: {}#{}", type.getName(), name);
    }

    /**
     * 刷新动态扩展（卸载后重新加载）
     *
     * @param name 扩展名称
     * @param className 类全限定名
     * @param sourceCode 源码（可选）
     * @param dynamicCompile true表示动态编译，false表示类路径加载
     */
    public void refreshDynamicExtension(String name, String className, String sourceCode, boolean dynamicCompile) throws Exception {
        log.info("Refreshing dynamic extension: {}#{}", type.getName(), name);

        // 先卸载
        removeDynamicExtension(name);

        // 重新注册
        registerDynamicExtension(name, className, sourceCode, dynamicCompile);

        log.info("Successfully refreshed dynamic extension: {}#{}", type.getName(), name);
    }

    /**
     * 检查扩展是否存在
     */
    public boolean hasExtension(String name) {
        return getExtensionClasses().containsKey(name);
    }

    /**
     * 获取所有已注册的扩展名
     */
    public Set<String> getSupportedExtensions() {
        return new HashSet<>(getExtensionClasses().keySet());
    }

    /**
     * 清除缓存（用于测试或热加载）
     */
    public void clearCache() {
        cachedClasses.set(null);
        cachedInstances.clear();
        cachedActivates.clear();
        cachedWrapperClasses = null;
        cachedAdaptiveInstance.set(null);
        cachedAdaptiveClass = null;
    }

    /**
     * 创建自适应扩展实例
     * 触发全量加载（确保cachedAdaptiveClass被扫描到），
     * 如果存在@AgentAdaptive标注的类则返回，否则返回null走动态代理路径
     */
    @SuppressWarnings("unchecked")
    private T createAdaptiveExtension() {
        Class<?> adaptiveClass = getAdaptiveExtensionClass();
        if (adaptiveClass != null) {
            try {
                return (T) registry.createInstanceWithInjection(adaptiveClass, extensionFactory);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create adaptive extension for " + type.getName(), e);
            }
        }
        // 与Dubbo不同：Dubbo动态编译生成自适应类，这里用JDK动态代理实现（有反射开销但避免了编译依赖）
        return AdaptiveExtensionProxyFactory.createProxy(type, this);
    }

    private Class<?> getAdaptiveExtensionClass() {
        getExtensionClasses();
        return cachedAdaptiveClass;
    }

    private void loadResource(URL resourceURL, Map<String, Class<?>> classes) {
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
                            loadClass(extensionName, className, resourceURL, classes);
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
    private void loadClass(String name, String className, URL resourceURL, Map<String, Class<?>> classes) throws ClassNotFoundException {
        // 对于静态加载的拓展类默认都使用的类加载器是当前线程的类加载器也就是AppClassLoader
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

        if (isWrapperClass(clazz)) {
            Set<Class<?>> wrappers = cachedWrapperClasses;
            if (wrappers == null) {
                cachedWrapperClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
                wrappers = cachedWrapperClasses;
            }
            wrappers.add(clazz);
            return;
        }

        // 普通扩展类处理
        if (name == null || name.isEmpty()) {
            throw new IllegalStateException(
                String.format("No extension name for the class %s in the config %s",
                    className, resourceURL));
        }

        // 缓存扩展Class
        putStaticClass(name, clazz, classes);

        // 缓存Activate注解
        cacheActivateAnnotation(name, clazz);
    }

    private void cacheActivateAnnotation(String name, Class<?> clazz) {
        AgentActivate activate = clazz.getAnnotation(AgentActivate.class);
        if (activate != null) {
            cachedActivates.put(name, activate);
        }
    }

    /**
     * 判断是否是Wrapper类
     * Wrapper类的标志是拥有一个参数为SPI接口类型的构造器
     */
    private boolean isWrapperClass(Class<?> clazz) {
        try {
            clazz.getConstructor(type);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
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
