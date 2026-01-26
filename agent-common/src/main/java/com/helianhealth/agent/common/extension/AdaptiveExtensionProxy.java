package com.helianhealth.agent.common.extension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Agent Adaptive Extension Proxy
 *
 * 自适应扩展代理，根据@AgentAdaptive注解配置，从方法参数对象中提取属性值作为扩展名
 *
 * 扩展名提取策略：
 * 1. 读取方法上的@AgentAdaptive注解的value值
 * 2. 将value转换为getter方法名（如 "nodeType" -> "getNodeType"）
 * 3. 从参数对象中调用该getter获取扩展名
 *
 * 示例：
 * <pre>
 * {@code @AgentAdaptive("nodeType")}
 * Map<String,Object> doInvoke(InterfaceWorkflowNode flowNode, List params);
 *
 * // 调用时会执行 flowNode.getNodeType() 获取扩展名
 * </pre>
 *
 * @author Agent Team
 */
class AdaptiveExtensionProxy {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveExtensionProxy.class);

    /**
     * 创建自适应代理实例
     */
    @SuppressWarnings("unchecked")
    static <T> T createProxy(Class<T> interfaceClass, AgentExtensionLoader<T> loader) {
        return (T) Proxy.newProxyInstance(
            interfaceClass.getClassLoader(),
            new Class<?>[]{interfaceClass},
            new AdaptiveInvocationHandler<>(interfaceClass, loader)
        );
    }

    /**
     * 自适应调用处理器
     */
    private static class AdaptiveInvocationHandler<T> implements InvocationHandler {

        private final Class<T> interfaceClass;
        private final AgentExtensionLoader<T> loader;

        AdaptiveInvocationHandler(Class<T> interfaceClass, AgentExtensionLoader<T> loader) {
            this.interfaceClass = interfaceClass;
            this.loader = loader;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 处理Object方法
            String methodName = method.getName();
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(methodName)) {
                return "AdaptiveProxy@" + interfaceClass.getSimpleName();
            }

            // 获取方法上的@AgentAdaptive注解
            AgentAdaptive adaptive = method.getAnnotation(AgentAdaptive.class);

            // 提取扩展名
            String extensionName = null;

            if (adaptive != null && adaptive.value().length > 0) {
                // 按@AgentAdaptive配置的属性名提取
                extensionName = extractByAdaptiveConfig(adaptive.value(), args);
            }
            // 降级：使用默认扩展
            if (extensionName == null || extensionName.isEmpty()) {
                extensionName = loader.getDefaultExtensionName();
            }

            if (extensionName == null || extensionName.isEmpty()) {
                throw new IllegalStateException(
                    "Cannot determine extension name for " + interfaceClass.getName() +
                    "." + methodName + "(), please check @AgentAdaptive configuration");
            }

            // 获取扩展并调用
            T extension = loader.getExtension(extensionName);
            if (extension == null) {
                throw new IllegalStateException(
                    "Extension not found: " + interfaceClass.getName() + "#" + extensionName);
            }

            log.debug("Adaptive select extension: {}#{} for method {}",
                    interfaceClass.getSimpleName(), extensionName, methodName);

            return method.invoke(extension, args);
        }

        /**
         * 根据@AgentAdaptive配置的属性名提取扩展名
         *
         * @param keys 配置的属性名数组，如 {"nodeType", "type"}
         * @param args 方法参数
         * @return 扩展名
         */
        private String extractByAdaptiveConfig(String[] keys, Object[] args) {
            if (args == null || args.length == 0) {
                return null;
            }

            // 遍历配置的key（按优先级）
            for (String key : keys) {
                // 构造getter方法名
                String getterName = "get" + Character.toUpperCase(key.charAt(0)) + key.substring(1);

                // 从所有参数对象中查找
                for (Object arg : args) {
                    if (arg == null) {
                        continue;
                    }

                    String value = invokeGetter(arg, getterName);
                    if (value != null && !value.isEmpty()) {
                        return value;
                    }
                }
            }

            return null;
        }

        /**
         * 反射调用getter方法
         *
         * @param obj 目标对象
         * @param getterName getter方法名
         * @return getter返回值的字符串形式，失败返回null
         */
        private String invokeGetter(Object obj, String getterName) {
            try {
                Method getter = obj.getClass().getMethod(getterName);
                Object result = getter.invoke(obj);

                if (result == null) {
                    return null;
                }

                // 处理枚举类型
                if (result instanceof Enum) {
                    return ((Enum<?>) result).name().toLowerCase();
                }

                // 其他类型转字符串
                String value = result.toString().trim();
                return value.isEmpty() ? null : value;

            } catch (NoSuchMethodException e) {
                // 方法不存在，静默忽略
                return null;
            } catch (Exception e) {
                log.debug("Failed to invoke {}.{}: {}",
                        obj.getClass().getSimpleName(), getterName, e.getMessage());
                return null;
            }
        }
    }
}
