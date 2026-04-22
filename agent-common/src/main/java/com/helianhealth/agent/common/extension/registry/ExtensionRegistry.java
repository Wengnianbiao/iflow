package com.helianhealth.agent.common.extension.registry;

import com.helianhealth.agent.common.extension.compiler.DynamicCompiler;
import com.helianhealth.agent.common.extension.AgentExtensionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent Extension Registry
 * <p>
 * 扩展注册中心，“类加载 + 实例创建”工厂
 * <p>
 * 职责边界：
 * 1. 动态编译：从源码编译加载 Class，管理动态 ClassLoader 生命周期
 * 2. 类加载：从源码编译或类路径加载 Class
 * 3. 实例创建：通过构造器注入创建扩展实例（不缓存，缓存由 AgentExtensionLoader 管理）
 * <p>
 * 注意：Class 映射由 AgentExtensionLoader 自行维护，本类不存储扩展名到 Class 的映射。
 */
public class ExtensionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ExtensionRegistry.class);

    private static final ExtensionRegistry INSTANCE = new ExtensionRegistry();

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

    // ==================== 类加载 ====================

    /**
     * 加载扩展类（支持动态编译和类路径加载）
     *
     * @param className 实现类全限定名
     * @param sourceCode 源代码（为空则从类路径加载）
     * @param dynamicCompile true表示动态编译（独立ClassLoader），false表示类路径加载(默认)
     * @return 加载后的 Class
     */
    public Class<?> loadClass(String className, String sourceCode, boolean dynamicCompile) throws Exception {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Class name cannot be null or empty");
        }

        if (dynamicCompile) {
            log.debug("Dynamic compilation mode: {}", className);
            return compileClass(className, sourceCode);
        } else {
            // 对于非动态编译模式，使用的类加载器是和当前线程绑定的类加载器
            // 本身在项目启动时这个类就已经由JVM加载了,这里只是确保加载,forName里面是个native方法)
            log.debug("Dynamic from classpath: {}", className);
            return Class.forName(className, true, parentClassLoader);
        }
    }

    // ==================== ClassLoader生命周期 ====================

    /**
     * 卸载动态编译类的 ClassLoader
     * <p>
     * 如果指定 className 没有对应的动态 ClassLoader（即静态加载的类），则无操作。
     *
     * @param className 类全限定名
     */
    public void unloadDynamicClass(String className) {
        if (className == null || className.isEmpty()) {
            return;
        }
        DynamicCompiler.DynamicClassLoader classLoader = classLoaderCache.remove(className);
        if (classLoader != null) {
            classLoader.unload();
            log.debug("Unloaded dynamic class: {}", className);
        }
    }

    /**
     * 清空所有动态 ClassLoader
     */
    public void clear() {
        int count = classLoaderCache.size();

        for (DynamicCompiler.DynamicClassLoader loader : classLoaderCache.values()) {
            loader.unload();
        }
        classLoaderCache.clear();

        if (count > 0) {
            log.debug("Cleared {} dynamic class loaders", count);
        }
    }

    // ==================== IOC注入 ====================

    /**
     * 创建实例并通过构造器注入依赖
     * <p>
     * 约束：扩展类有且仅有一个 public 构造器
     * <ul>
     *   <li>无参构造器：直接实例化</li>
     *   <li>带参构造器：通过 ExtensionFactory 获取所有参数的依赖并注入</li>
     * </ul>
     *
     * @param clazz            扩展类
     * @param extensionFactory 扩展工厂（用于依赖注入，无参构造器时可为 null）
     * @throws IllegalStateException 当构造器数量不为 1，或依赖无法注入时抛出
     */
    public Object createInstanceWithInjection(Class<?> clazz, AgentExtensionFactory extensionFactory) throws Exception {
        return createInstanceWithInjection(clazz, extensionFactory, null);
    }

    /**
     * 创建实例并通过构造器注入依赖，支持预置依赖
     * <p>
     * 预置依赖场景：Wrapper 类的构造器中包含 SPI 接口类型的参数，
     * 该参数是被包装的实例，而非从 Factory 获取。
     *
     * @param clazz            扩展类
     * @param extensionFactory 扩展工厂
     * @param presetDependency 预置依赖（当构造器参数类型匹配时优先使用，可为 null）
     */
    public Object createInstanceWithInjection(Class<?> clazz, AgentExtensionFactory extensionFactory,
                                               Object presetDependency) throws Exception {
        Constructor<?>[] constructors = clazz.getConstructors();

        if (constructors.length != 1) {
            throw new IllegalStateException(
                String.format("Extension class %s must have exactly one public constructor, but found %d. " +
                    "SPI framework requires a single explicit constructor for dependency injection.",
                    clazz.getName(), constructors.length));
        }

        Constructor<?> constructor = constructors[0];
        Class<?>[] paramTypes = constructor.getParameterTypes();

        // 无参构造器，直接实例化
        if (paramTypes.length == 0) {
            log.debug("Creating instance with no-arg constructor: {}", clazz.getSimpleName());
            return constructor.newInstance();
        }

        // 带参构造器，通过 ExtensionFactory 获取所有依赖
        if (extensionFactory == null) {
            throw new IllegalStateException(
                String.format("Extension class %s requires dependencies via constructor, " +
                    "but ExtensionFactory is not available.", clazz.getName()));
        }

        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            Object dependency;
            // 预置依赖优先：当参数类型与预置依赖匹配时优先使用（Wrapper场景）
            if (paramType.isInstance(presetDependency)) {
                dependency = presetDependency;
            } else {
                dependency = extensionFactory.getExtension(paramType, paramType.getName());
            }
            if (dependency == null) {
                throw new IllegalStateException(
                    String.format("Failed to inject constructor parameter [%d] of type %s for %s. " +
                        "No available extension or Spring Bean found.",
                        i, paramType.getName(), clazz.getName()));
            }
            args[i] = dependency;
        }

        log.debug("Creating instance with constructor injection: {} ({} params)",
                clazz.getSimpleName(), paramTypes.length);

        return constructor.newInstance(args);
    }


    // ==================== 动态编译 ====================

    /**
     * 编译Java源码
     */
    private Class<?> compileClass(String className, String sourceCode) throws Exception {
        DynamicCompiler.CompileResult result = DynamicCompiler.getInstance().compile(className, sourceCode);
        // 缓存类加载器以支持后续卸载
        classLoaderCache.put(className, result.getClassLoader());

        return result.getClazz();
    }
}
