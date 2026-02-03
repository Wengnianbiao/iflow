package com.helianhealth.agent.common.extension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Agent Adaptive Extension Proxy
 * <p>
 * Adaptive extension proxy that extracts attribute values from method parameter objects
 * as extension names based on {@code @AgentAdaptive} annotation configuration.
 * <p>
 * <b>Extension Name Extraction Strategy:</b>
 * <ol>
 * <li>Read the value from {@code @AgentAdaptive} annotation on the method</li>
 * <li>Convert the value to getter method name (e.g., "nodeType" -> "getNodeType")</li>
 * <li>Invoke the getter on parameter objects to retrieve the extension name</li>
 * </ol>
 * <p>
 * <b>Usage Example:</b>
 * <pre>{@code
 * @AgentAdaptive("nodeType")
 * Map<String, Object> doInvoke(InterfaceWorkflowNode flowNode, List params);
 *
 * // When invoked, it will call flowNode.getNodeType() to get the extension name
 * }</pre>
 */
class AdaptiveExtensionProxy {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveExtensionProxy.class);

    /**
     * Create an adaptive proxy instance
     *
     * @param interfaceClass the extension interface class
     * @param loader the extension loader
     * @param <T> the extension interface type
     * @return the adaptive proxy instance
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
     * Adaptive invocation handler for dynamic extension selection
     *
     * @param <T> the extension interface type
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
            // Handle Object methods
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

            // Get @AgentAdaptive annotation from method
            AgentAdaptive adaptive = method.getAnnotation(AgentAdaptive.class);

            // Extract extension name
            String extensionName = null;

            if (adaptive != null && adaptive.value().length > 0) {
                // Extract by @AgentAdaptive configured property name
                extensionName = extractByAdaptiveConfig(adaptive.value(), args);
            }
            // Fallback: use default extension
            if (extensionName == null || extensionName.isEmpty()) {
                extensionName = loader.getDefaultExtensionName();
            }

            if (extensionName == null || extensionName.isEmpty()) {
                throw new IllegalStateException(
                    "Cannot determine extension name for " + interfaceClass.getName() +
                    "." + methodName + "(), please check @AgentAdaptive configuration");
            }

            // Get extension and invoke
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
         * Extract extension name by property names configured in {@code @AgentAdaptive}
         *
         * @param keys configured property name array, e.g., {"nodeType", "type"}
         * @param args method arguments
         * @return extension name, or null if not found
         */
        private String extractByAdaptiveConfig(String[] keys, Object[] args) {
            if (args == null || args.length == 0) {
                return null;
            }

            // Iterate configured keys (by priority)
            for (String key : keys) {
                // Construct getter method name
                String getterName = "get" + Character.toUpperCase(key.charAt(0)) + key.substring(1);

                // Search from all parameter objects
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
         * Invoke getter method via reflection
         *
         * @param obj target object
         * @param getterName getter method name
         * @return string representation of getter return value, or null if failed
         */
        private String invokeGetter(Object obj, String getterName) {
            try {
                Method getter = obj.getClass().getMethod(getterName);
                Object result = getter.invoke(obj);

                if (result == null) {
                    return null;
                }

                // Handle enum type
                if (result instanceof Enum) {
                    return ((Enum<?>) result).name().toLowerCase();
                }

                // Convert other types to string
                String value = result.toString().trim();
                return value.isEmpty() ? null : value;

            } catch (NoSuchMethodException e) {
                // Method not found, silently ignore
                return null;
            } catch (Exception e) {
                log.debug("Failed to invoke {}.{}: {}",
                        obj.getClass().getSimpleName(), getterName, e.getMessage());
                return null;
            }
        }
    }
}
