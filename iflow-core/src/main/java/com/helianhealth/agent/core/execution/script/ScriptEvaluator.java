package com.helianhealth.agent.core.execution.script;

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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * GraalJS 脚本求值器。
 *
 * <p>脚本内通过全局 {@code $} 访问任务 input；{@code XML} 提供 XML 解析/序列化。
 */
public final class ScriptEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptEvaluator.class);
    private static final XmlBridge XML_BRIDGE = new XmlBridge();

    private static final int DEFAULT_MAX_EXECUTION_SECONDS = 4;
    private static final int DEFAULT_CONTEXT_POOL_SIZE = 10;
    private static final boolean DEFAULT_CONTEXT_POOL_ENABLED = false;
    private static final int DEFAULT_SOURCE_CACHE_SIZE = 1024;

    private static Duration maxExecutionTimeSeconds;
    private static ExecutorService executorService;
    private static BlockingQueue<ScriptExecutionContext> contextPool;
    private static boolean contextPoolEnabled;
    private static boolean initialized;
    private static int sourceCacheMaxSize = DEFAULT_SOURCE_CACHE_SIZE;

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

    public static void validateBindingName(String name) {
        if (name == null || !name.matches("[A-Za-z_$][A-Za-z0-9_$]*") || RESERVED_BINDINGS.contains(name)) {
            throw new IllegalArgumentException("无效或保留的循环变量名: " + name);
        }
    }

    private ScriptEvaluator() {}

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

    public static synchronized void initializeWithDefaults() {
        if (initialized) {
            return;
        }
        int maxSeconds =
                Integer.parseInt(
                        getEnv(
                                "AGENT_SCRIPT_MAX_EXECUTION_SECONDS",
                                String.valueOf(DEFAULT_MAX_EXECUTION_SECONDS)));
        int poolSize =
                Integer.parseInt(
                        getEnv(
                                "AGENT_SCRIPT_CONTEXT_POOL_SIZE",
                                String.valueOf(DEFAULT_CONTEXT_POOL_SIZE)));
        boolean poolEnabled =
                Boolean.parseBoolean(
                        getEnv(
                                "AGENT_SCRIPT_CONTEXT_POOL_ENABLED",
                                String.valueOf(DEFAULT_CONTEXT_POOL_ENABLED)));
        sourceCacheMaxSize =
                Integer.parseInt(
                        getEnv(
                                "AGENT_SCRIPT_SOURCE_CACHE_SIZE",
                                String.valueOf(DEFAULT_SOURCE_CACHE_SIZE)));
        initialize(maxSeconds, poolSize, poolEnabled, null);
    }

    public static synchronized void initialize(
            int maxSeconds, int contextPoolSize, boolean poolEnabled, ExecutorService executor) {
        if (initialized) {
            return;
        }
        maxExecutionTimeSeconds = Duration.ofSeconds(maxSeconds);
        executorService = executor != null ? executor : Executors.newCachedThreadPool();
        contextPoolEnabled = poolEnabled;
        if (!contextPoolEnabled) {
            contextPool = null;
        } else {
            contextPool = new LinkedBlockingQueue<>(contextPoolSize);
            for (int i = 0; i < contextPoolSize; i++) {
                contextPool.offer(new ScriptExecutionContext(createNewContext()));
            }
        }
        initialized = true;
    }

    private static String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null ? value : defaultValue;
    }

    private static void ensureInitialized() {
        if (!initialized) {
            initializeWithDefaults();
        }
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
            int len = java.lang.reflect.Array.getLength(input);
            List<Object> copy = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                copy.add(deepCopy(java.lang.reflect.Array.get(input, i)));
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
        if (SOURCE_CACHE.size() >= sourceCacheMaxSize) {
            SOURCE_CACHE.clear();
        }
        Source source = Source.newBuilder("js", script, "inline").cached(true).buildLiteral();
        Source existing = SOURCE_CACHE.putIfAbsent(script, source);
        return existing != null ? existing : source;
    }

    public static Object eval(String script, Object input) {
        return eval(script, input, Map.of());
    }

    public static Object eval(String script, Object input, Map<String, Object> localBindings) {
        localBindings.keySet().forEach(ScriptEvaluator::validateBindingName);
        ensureInitialized();
        final Source source = getSource(script);

        if (contextPoolEnabled) {
            ScriptExecutionContext scriptContext = null;
            try {
                scriptContext = contextPool.take();
                final ScriptExecutionContext finalScriptContext = scriptContext;
                finalScriptContext.prepareBindings(input, localBindings);
                Future<Value> futureResult =
                        executorService.submit(() -> finalScriptContext.getContext().eval(source));
                Value value =
                        futureResult.get(maxExecutionTimeSeconds.getSeconds(), TimeUnit.SECONDS);
                return getObject(value);
            } catch (TimeoutException e) {
                if (scriptContext != null) {
                    interrupt(scriptContext.getContext());
                }
                throw new IllegalStateException(
                        String.format(
                                "Script not evaluated within %d seconds, interrupted.",
                                maxExecutionTimeSeconds.getSeconds()));
            } catch (ExecutionException ee) {
                handlePolyglotException(ee);
                return null;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Script execution interrupted: " + ie.getMessage());
            } finally {
                if (scriptContext != null) {
                    scriptContext.clearBindings();
                    if (!contextPool.offer(scriptContext)) {
                        scriptContext.getContext().close();
                    }
                }
            }
        }

        try (Context context = createNewContext()) {
            final Value jsBindings = context.getBindings("js");
            jsBindings.putMember("$", toScriptValue(input));
            jsBindings.putMember("XML", XML_BRIDGE);
            bindLocals(jsBindings, localBindings);
            final Future<Value> futureResult = executorService.submit(() -> context.eval(source));
            Value value = futureResult.get(maxExecutionTimeSeconds.getSeconds(), TimeUnit.SECONDS);
            return getObject(value);
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                    String.format(
                            "Script not evaluated within %d seconds, interrupted.",
                            maxExecutionTimeSeconds.getSeconds()));
        } catch (ExecutionException ee) {
            handlePolyglotException(ee);
            return null;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Script execution interrupted: " + ie.getMessage());
        }
    }

    private static void bindLocals(Value bindings, Map<String, Object> locals) {
        for (String name : locals.keySet()) {
            if (bindings.hasMember(name)) {
                throw new IllegalArgumentException("循环变量与 JS 全局变量冲突: " + name);
            }
        }
        locals.forEach((name, value) -> bindings.putMember(name, toScriptValue(value)));
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

    private static void handlePolyglotException(ExecutionException ee) {
        if (ee.getCause() instanceof PolyglotException pe) {
            SourceSection sourceSection = pe.getSourceLocation();
            if (sourceSection == null) {
                throw new IllegalStateException("Error evaluating script: " + pe.getMessage());
            }
            throw new IllegalStateException(
                    "Error evaluating script: "
                            + pe.getMessage()
                            + " at line "
                            + sourceSection.getStartLine());
        }
        throw new IllegalStateException("Error evaluating script: " + ee.getMessage());
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

    private static void interrupt(Context context) {
        try {
            context.interrupt(Duration.ZERO);
        } catch (TimeoutException ignored) {
            // expected
        }
    }

    private static final class ScriptExecutionContext {
        private final Context context;
        private final Value bindings;
        private Set<String> localNames = Set.of();

        ScriptExecutionContext(Context context) {
            this.context = context;
            this.bindings = context.getBindings("js");
        }

        Context getContext() {
            return context;
        }

        void prepareBindings(Object input, Map<String, Object> locals) {
            bindings.putMember("$", toScriptValue(input));
            bindings.putMember("XML", XML_BRIDGE);
            bindLocals(bindings, locals);
            localNames = new HashSet<>(locals.keySet());
        }

        void clearBindings() {
            localNames.forEach(bindings::removeMember);
            localNames = Set.of();
            bindings.removeMember("$");
            bindings.removeMember("XML");
        }
    }
}
