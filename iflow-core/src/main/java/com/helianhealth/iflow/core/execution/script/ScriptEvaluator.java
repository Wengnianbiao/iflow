package com.helianhealth.iflow.core.execution.script;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * GraalJS 脚本求值器。
 *
 * <p>脚本通过 {@code $} 访问当前输入，{@code XML} 提供 XML 解析和序列化。
 */
public final class ScriptEvaluator implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptEvaluator.class);
    private static final int SOURCE_CACHE_MAX_SIZE =
            Integer.parseInt(getEnv("IFLOW_SCRIPT_SOURCE_CACHE_SIZE", "1024"));

    private static final Engine ENGINE = buildEngine();
    private static final ConcurrentMap<String, Source> SOURCE_CACHE = new ConcurrentHashMap<>();

    private static final Set<String> RESERVED_BINDINGS = Set.of(
            "$", "XML", "arguments", "eval", "undefined", "NaN", "Infinity", "globalThis",
            "Object", "Function", "Boolean", "Symbol", "Number", "BigInt", "Math", "Date",
            "String", "RegExp", "Array", "JSON", "Map", "Set", "WeakMap", "WeakSet",
            "Promise", "Reflect", "Proxy", "Intl", "Atomics", "WebAssembly", "console",
            "Error", "TypeError", "RangeError", "SyntaxError", "ReferenceError", "URIError",
            "ArrayBuffer", "SharedArrayBuffer", "DataView", "Int8Array", "Uint8Array",
            "Uint8ClampedArray", "Int16Array", "Uint16Array", "Int32Array", "Uint32Array",
            "Float32Array", "Float64Array", "BigInt64Array", "BigUint64Array",
            "parseInt", "parseFloat", "isNaN", "isFinite", "encodeURI", "decodeURI",
            "encodeURIComponent", "decodeURIComponent", "escape", "unescape",
            "break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "export", "extends", "finally", "for", "function", "if",
            "import", "in", "instanceof", "new", "return", "super", "switch", "this", "throw",
            "try", "typeof", "var", "void", "while", "with", "yield", "let", "static", "enum",
            "await", "implements", "package", "protected", "interface", "private", "public",
            "null", "true", "false");

    private final Map<String, Object> applicationBindings;
    private final int maxExecutionSeconds;
    private final ExecutorService executorService;
    private final Deque<Context> contextPool;
    private boolean closed;

    public ScriptEvaluator(List<ScriptBindingProvider> providers) {
        applicationBindings = readApplicationBindings(providers);
        maxExecutionSeconds = Integer.parseInt(getEnv("IFLOW_SCRIPT_MAX_EXECUTION_SECONDS", "4"));
        if (Boolean.parseBoolean(getEnv("IFLOW_SCRIPT_CONTEXT_POOL_ENABLED", "false"))) {
            int poolSize = Integer.parseInt(getEnv("IFLOW_SCRIPT_CONTEXT_POOL_SIZE", "10"));
            if (poolSize <= 0) {
                throw new IllegalArgumentException("Script context pool size must be positive");
            }
            contextPool = new ArrayDeque<>(poolSize);
            for (int i = 0; i < poolSize; i++) {
                contextPool.addLast(createNewContext());
            }
        } else {
            contextPool = null;
        }
        executorService = Executors.newCachedThreadPool();
    }

    public void validateBindingName(String name) {
        validateIdentifier(name);
        if (applicationBindings.containsKey(name)) {
            throw new IllegalArgumentException("与已注册的 JS 绑定重名: " + name);
        }
    }

    private static void validateIdentifier(String name) {
        if (name == null || !name.matches("[A-Za-z_$][A-Za-z0-9_$]*") || RESERVED_BINDINGS.contains(name)) {
            throw new IllegalArgumentException("无效或保留的 JS 绑定名称: " + name);
        }
    }

    private static Map<String, Object> readApplicationBindings(List<ScriptBindingProvider> providers) {
        Map<String, Object> registered = new LinkedHashMap<>();
        for (ScriptBindingProvider provider : providers) {
            for (Map.Entry<String, Object> binding : provider.bindings().entrySet()) {
                String name = binding.getKey();
                validateIdentifier(name);
                if (registered.containsKey(name)) {
                    throw new IllegalArgumentException("重复注册的 JS 绑定名称: " + name);
                }
                registered.put(name, binding.getValue());
            }
        }
        if (!registered.isEmpty()) {
            try (Context context = createNewContext()) {
                validateAvailableNames(context.getBindings("js"), registered.keySet());
            }
        }
        registered.put("XML", new XmlBridge());
        return Collections.unmodifiableMap(registered);
    }

    private static Engine buildEngine() {
        Engine engine =
                Engine.newBuilder("js")
                        .allowExperimentalOptions(true)
                        .option("engine.WarnInterpreterOnly", "false")
                        .option("js.load", "false")
                        .option("js.print", "false")
                        .option("js.console", "false")
                        .build();
        LOGGER.info(
                "GraalVM polyglot engine: implementation={}, version={}",
                engine.getImplementationName(),
                engine.getVersion());
        return engine;
    }

    private static String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null ? value : defaultValue;
    }

    private static Context createNewContext() {
        HostAccess hostAccess =
                HostAccess.newBuilder(HostAccess.ALL)
                        .denyAccess(Class.class)
                        .denyAccess(ClassLoader.class)
                        .denyAccess(java.lang.reflect.Method.class)
                        .denyAccess(java.lang.reflect.Field.class)
                        .denyAccess(java.lang.reflect.Constructor.class)
                        .denyAccess(java.lang.reflect.Array.class)
                        .denyAccess(Runtime.class)
                        .denyAccess(ProcessBuilder.class)
                        .denyAccess(Process.class)
                        .denyAccess(System.class)
                        .denyAccess(Thread.class)
                        .denyAccess(ThreadGroup.class)
                        .build();
        return Context.newBuilder("js")
                .engine(ENGINE)
                .allowHostAccess(hostAccess)
                .allowHostClassLoading(false)
                .allowNativeAccess(false)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowIO(IOAccess.NONE)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .build();
    }

    public static Object deepCopy(Object input) {
        if (input == null) {
            return null;
        }
        if (input instanceof Map<?, ?> m) {
            Map<Object, Object> copy = new LinkedHashMap<>(m.size());
            for (Map.Entry<?, ?> e : m.entrySet()) {
                copy.put(e.getKey(), deepCopy(e.getValue()));
            }
            return copy;
        }
        if (input instanceof List<?> l) {
            List<Object> copy = new ArrayList<>(l.size());
            for (Object item : l) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        if (input instanceof Set<?> s) {
            Set<Object> copy = new LinkedHashSet<>(s.size());
            for (Object item : s) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        Class<?> cls = input.getClass();
        if (cls.isArray()) {
            int len = Array.getLength(input);
            List<Object> copy = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                copy.add(deepCopy(Array.get(input, i)));
            }
            return copy;
        }
        return input;
    }

    private static Source getSource(String script) {
        Source cached = SOURCE_CACHE.get(script);
        if (cached != null) {
            return cached;
        }
        if (SOURCE_CACHE.size() >= SOURCE_CACHE_MAX_SIZE) {
            SOURCE_CACHE.clear();
        }
        Source source = Source.newBuilder("js", script, "inline").cached(true).buildLiteral();
        Source existing = SOURCE_CACHE.putIfAbsent(script, source);
        return existing != null ? existing : source;
    }

    public Boolean evalBool(String script, Object input) {
        return toBoolean(eval(script, input));
    }

    public Object eval(String script, Object input) {
        return eval(script, input, Map::of);
    }

    public Object eval(String script, Object input, ScriptBindingProvider provider) {
        Map<String, Object> bindings = new LinkedHashMap<>(applicationBindings);
        bindings.put("$", input);
        for (Map.Entry<String, Object> binding : provider.bindings().entrySet()) {
            validateBindingName(binding.getKey());
            bindings.put(binding.getKey(), binding.getValue());
        }

        Source source = getSource(script);
        Context context = acquireContext();
        boolean finished = false;
        Future<Object> evaluation = null;
        try {
            Value jsBindings = context.getBindings("js");
            validateAvailableNames(jsBindings, bindings.keySet());
            bindings.forEach((name, value) -> jsBindings.putMember(name, toScriptValue(deepCopy(value))));
            evaluation = executorService.submit(() -> getObject(context.eval(source)));
            Object result = evaluation.get(maxExecutionSeconds, TimeUnit.SECONDS);
            finished = true;
            return result;
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                    "Script not evaluated within %d seconds, interrupted.".formatted(maxExecutionSeconds));
        } catch (ExecutionException e) {
            finished = true;
            throw scriptFailure(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Script execution interrupted", e);
        } finally {
            if (!finished && evaluation != null) {
                evaluation.cancel(true);
            }
            releaseContext(context, bindings.keySet(), finished);
        }
    }

    private static void validateAvailableNames(Value bindings, Set<String> names) {
        for (String name : names) {
            if (bindings.hasMember(name)) {
                throw new IllegalArgumentException("与现有 JS 全局变量重名: " + name);
            }
        }
    }

    private synchronized Context acquireContext() {
        try {
            while (!closed && contextPool != null && contextPool.isEmpty()) {
                wait();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Script execution interrupted", e);
        }
        if (closed) {
            throw new IllegalStateException("Script evaluator is closed");
        }
        return contextPool == null ? createNewContext() : contextPool.removeFirst();
    }

    private void releaseContext(Context context, Set<String> names, boolean finished) {
        if (contextPool == null) {
            context.close(!finished);
        } else if (finished && clearBindings(context, names)) {
            returnPooledContext(context);
        } else {
            try {
                context.close(!finished);
            } finally {
                replacePooledContext();
            }
        }
    }

    private static boolean clearBindings(Context context, Set<String> names) {
        try {
            Value bindings = context.getBindings("js");
            for (String name : names) {
                bindings.removeMember(name);
                if (bindings.hasMember(name)) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException e) {
            LOGGER.warn("Could not clear script bindings; discarding JS context", e);
            return false;
        }
    }

    private synchronized void replacePooledContext() {
        if (!closed) {
            returnPooledContext(createNewContext());
        }
    }

    private synchronized void returnPooledContext(Context context) {
        if (closed) {
            context.close();
        } else {
            contextPool.addLast(context);
            notifyAll();
        }
    }

    /** 显式暴露 JSON 成员，避免 Java Map 在 Object.keys / JSON.stringify 中表现为空对象。 */
    private static Object toScriptValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> members = new LinkedHashMap<>();
            map.forEach((key, item) -> members.put(String.valueOf(key), toScriptValue(item)));
            return ProxyObject.fromMap(members);
        }
        if (value instanceof List<?> list) {
            List<Object> items = new ArrayList<>(list.size());
            list.forEach(item -> items.add(toScriptValue(item)));
            return ProxyArray.fromList(items);
        }
        return jsonCompatibleScalar(value);
    }

    /**
     * 将泛型对象转换为布尔值。检查对象是否为布尔和类型返回布尔对象的值。检查对象是否为Number类型并返回当值大于0时为True。
     * @param input 将被检查以返回布尔值的通用对象。
     * @return 根据提供的输入返回True或False
     */
    public static Boolean toBoolean(Object input) {
        if (input instanceof Boolean) {
            return ((Boolean) input);
        } else if (input instanceof Number) {
            return ((Number) input).doubleValue() > 0;
        }
        return false;
    }

    /**
     * BigDecimal / BigInteger 对 GraalJS 不是 JS Number，而是带方法的 Java 对象。
     * 不转成数字的话，回写时会把 {@code longValueExact} 等方法展开成空对象。
     */
    private static Object jsonCompatibleScalar(Object value) {
        if (value instanceof BigDecimal decimal) {
            return jsonNumber(decimal);
        }
        if (value instanceof BigInteger integer) {
            return integer.bitLength() <= 63 ? integer.longValue() : integer.toString();
        }
        return value;
    }

    private static Number jsonNumber(BigDecimal decimal) {
        BigDecimal stripped = decimal.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            try {
                return stripped.longValueExact();
            } catch (ArithmeticException ignored) {
                return stripped.doubleValue();
            }
        }
        return stripped.doubleValue();
    }

    private static IllegalStateException scriptFailure(ExecutionException ee) {
        if (ee.getCause() instanceof PolyglotException pe) {
            SourceSection sourceSection = pe.getSourceLocation();
            if (sourceSection == null) {
                return new IllegalStateException("Error evaluating script: " + pe.getMessage());
            }
            return new IllegalStateException(
                    "Error evaluating script: "
                            + pe.getMessage()
                            + " at line "
                            + sourceSection.getStartLine());
        }
        return new IllegalStateException("Error evaluating script: " + ee.getMessage());
    }

    private static Object getObject(Value value) {
        if (value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            }
            if (value.fitsInLong()) {
                return value.asLong();
            }
            if (value.fitsInDouble()) {
                return value.asDouble();
            }
        }
        if (value.isHostObject()) {
            return jsonCompatibleScalar(value.asHostObject());
        }
        if (value.hasArrayElements()) {
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < value.getArraySize(); i++) {
                items.add(getObject(value.getArrayElement(i)));
            }
            return items;
        }

        Map<Object, Object> output = new HashMap<>();
        if (value.hasHashEntries()) {
            Value keys = value.getHashKeysIterator();
            while (keys.hasIteratorNextElement()) {
                Value key = keys.getIteratorNextElement();
                output.put(getObject(key), getObject(value.getHashValue(key)));
            }
        } else {
            for (String key : value.getMemberKeys()) {
                output.put(key, getObject(value.getMember(key)));
            }
        }
        return output;
    }

    @Override
    public synchronized void close() {
        closed = true;
        notifyAll();
        executorService.shutdownNow();
        if (contextPool != null) {
            Context context;
            while ((context = contextPool.pollFirst()) != null) {
                context.close();
            }
        }
    }
}
