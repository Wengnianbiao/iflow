package com.helianhealth.iflow.common.extension;

import com.helianhealth.iflow.common.extension.fixture.*;
import com.helianhealth.iflow.common.extension.fixture.DefaultTestExtension;
import com.helianhealth.iflow.common.extension.fixture.DynamicTestExtension;
import com.helianhealth.iflow.common.extension.fixture.TestExtension;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExtensionLoader 单元测试
 *
 * 测试策略：
 * - 使用真实的测试 SPI 接口和配置文件，验证完整的扩展加载机制
 * - 测试 fixture 定义在 fixture 包下，配置文件在 test/resources/META-INF/iflow/
 * - 每个测试后清理静态缓存，保证测试隔离
 */
class ExtensionLoaderTest {

    private ExtensionLoader<TestExtension> loader;

    @BeforeEach
    void setUp() {
        loader = ExtensionLoader.getExtensionLoader(TestExtension.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        // 清理实例缓存
        loader.clearCache();
        // 清理静态 EXTENSION_LOADERS 中的 TestExtension loader，保证测试隔离
        Field field = ExtensionLoader.class.getDeclaredField("EXTENSION_LOADERS");
        field.setAccessible(true);
        ConcurrentMap<Class<?>, ExtensionLoader<?>> loaders =
                (ConcurrentMap<Class<?>, ExtensionLoader<?>>) field.get(null);
        loaders.remove(TestExtension.class);
    }

    // ==================== getExtensionLoader ====================

    @Nested
    @DisplayName("getExtensionLoader")
    class GetExtensionLoaderTests {

        @Test
        @DisplayName("正常获取Loader")
        void getExtensionLoader_valid() {
            ExtensionLoader<TestExtension> result =
                    ExtensionLoader.getExtensionLoader(TestExtension.class);
            assertNotNull(result);
        }

        @Test
        @DisplayName("同一接口返回同一Loader实例（单例）")
        void getExtensionLoader_singleton() {
            ExtensionLoader<TestExtension> loader1 =
                    ExtensionLoader.getExtensionLoader(TestExtension.class);
            ExtensionLoader<TestExtension> loader2 =
                    ExtensionLoader.getExtensionLoader(TestExtension.class);
            assertSame(loader1, loader2);
        }

        @Test
        @DisplayName("null类型抛出IllegalArgumentException")
        void getExtensionLoader_null() {
            assertThrows(IllegalArgumentException.class,
                    () -> ExtensionLoader.getExtensionLoader((Class<?>) null));
        }

        @Test
        @DisplayName("空字符串 FQCN 抛出 IllegalArgumentException")
        void getExtensionLoader_blankFqcn() {
            assertThrows(IllegalArgumentException.class,
                    () -> ExtensionLoader.getExtensionLoader((String) null));
            assertThrows(IllegalArgumentException.class,
                    () -> ExtensionLoader.getExtensionLoader(""));
        }

        @Test
        @DisplayName("FQCN 类不存在抛出 IllegalArgumentException")
        void getExtensionLoader_classNotFound() {
            assertThrows(IllegalArgumentException.class,
                    () -> ExtensionLoader.getExtensionLoader("com.no.such.Class"));
        }

        @Test
        @DisplayName("非接口类型抛出IllegalArgumentException")
        void getExtensionLoader_notInterface() {
            assertThrows(IllegalArgumentException.class,
                    () -> ExtensionLoader.getExtensionLoader(String.class));
        }

        @Test
        @DisplayName("无@SPI注解的接口抛出IllegalArgumentException")
        void getExtensionLoader_noAnnotation() {
            assertThrows(IllegalArgumentException.class,
                    () -> ExtensionLoader.getExtensionLoader(Runnable.class));
        }
    }

    // ==================== getExtension ====================

    @Nested
    @DisplayName("getExtension")
    class GetExtensionTests {

        @Test
        @DisplayName("按名称获取扩展实例")
        void getExtension_byName() {
            TestExtension ext = loader.getExtension("default");
            assertNotNull(ext);
            assertEquals("default:hello", ext.echo("hello"));
        }

        @Test
        @DisplayName("获取custom扩展实例")
        void getExtension_custom() {
            TestExtension ext = loader.getExtension("custom");
            assertNotNull(ext);
            assertEquals("custom:hello", ext.echo("hello"));
        }

        @Test
        @DisplayName("同名扩展返回同一实例（单例缓存）")
        void getExtension_singleton() {
            TestExtension ext1 = loader.getExtension("default");
            TestExtension ext2 = loader.getExtension("default");
            assertSame(ext1, ext2);
        }

        @Test
        @DisplayName("null名称抛出IllegalArgumentException")
        void getExtension_nullName() {
            assertThrows(IllegalArgumentException.class,
                    () -> loader.getExtension(null));
        }

        @Test
        @DisplayName("空名称抛出IllegalArgumentException")
        void getExtension_emptyName() {
            assertThrows(IllegalArgumentException.class,
                    () -> loader.getExtension(""));
        }

        @Test
        @DisplayName("不存在的扩展名抛出IllegalStateException")
        void getExtension_notFound() {
            assertThrows(IllegalStateException.class,
                    () -> loader.getExtension("non-existent"));
        }
    }

    // ==================== getDefaultExtension ====================

    @Nested
    @DisplayName("getDefaultExtension")
    class GetDefaultExtensionTests {

        @Test
        @DisplayName("获取默认扩展（@SPI(\"default\")）")
        void getDefaultExtension_exists() {
            TestExtension ext = loader.getDefaultExtension();
            assertNotNull(ext);
            assertEquals("default:test", ext.echo("test"));
        }

        @Test
        @DisplayName("默认扩展名正确")
        void getDefaultExtensionName() {
            assertEquals("default", loader.getDefaultExtensionName());
        }
    }

    // ==================== hasExtension / getSupportedExtensions ====================

    @Nested
    @DisplayName("hasExtension / getSupportedExtensions")
    class QueryTests {

        @Test
        @DisplayName("检查已注册的扩展存在")
        void hasExtension_exists() {
            assertTrue(loader.hasExtension("default"));
            assertTrue(loader.hasExtension("custom"));
        }

        @Test
        @DisplayName("检查未注册的扩展不存在")
        void hasExtension_notExists() {
            assertFalse(loader.hasExtension("non-existent"));
        }

        @Test
        @DisplayName("获取所有支持的扩展名")
        void getSupportedExtensions() {
            Set<String> extensions = loader.getSupportedExtensions();
            assertTrue(extensions.contains("default"));
            assertTrue(extensions.contains("custom"));
            assertTrue(extensions.contains("activated"));
            assertTrue(extensions.contains("high-priority"));
            assertEquals(4, extensions.size());
        }
    }

    // ==================== addExtension / replaceExtension / removeExtension ====================

    @Nested
    @DisplayName("扩展生命周期管理")
    class ExtensionLifecycleTests {

        @Test
        @DisplayName("编程式添加扩展")
        void addExtension_success() {
            loader.addExtension("dynamic", DynamicTestExtension.class);

            assertTrue(loader.hasExtension("dynamic"));
            TestExtension ext = loader.getExtension("dynamic");
            assertEquals("dynamic:hello", ext.echo("hello"));
        }

        @Test
        @DisplayName("添加重复扩展名抛出异常")
        void addExtension_duplicate() {
            assertThrows(IllegalStateException.class,
                    () -> loader.addExtension("default", DefaultTestExtension.class));
        }

        @Test
        @DisplayName("添加null名称抛出异常")
        void addExtension_nullName() {
            assertThrows(IllegalArgumentException.class,
                    () -> loader.addExtension(null, DynamicTestExtension.class));
        }

        @Test
        @DisplayName("添加null类抛出异常")
        void addExtension_nullClass() {
            assertThrows(IllegalArgumentException.class,
                    () -> loader.addExtension("test", null));
        }

        @Test
        @DisplayName("添加不实现接口的类抛出异常")
        void addExtension_notImplement() {
            assertThrows(IllegalArgumentException.class,
                    () -> loader.addExtension("bad", String.class));
        }

        @Test
        @DisplayName("替换已有扩展")
        void replaceExtension_success() {
            // 先确认原始实例
            TestExtension original = loader.getExtension("default");
            assertEquals("default:x", original.echo("x"));

            // 替换为 DynamicTestExtension
            loader.replaceExtension("default", DynamicTestExtension.class);

            // 替换后获取的是新实例
            TestExtension replaced = loader.getExtension("default");
            assertEquals("dynamic:x", replaced.echo("x"));
        }

        @Test
        @DisplayName("替换不存在的扩展抛出异常")
        void replaceExtension_notFound() {
            assertThrows(IllegalStateException.class,
                    () -> loader.replaceExtension("non-existent", DynamicTestExtension.class));
        }

        @Test
        @DisplayName("移除扩展")
        void removeExtension_success() {
            assertTrue(loader.hasExtension("custom"));

            loader.removeExtension("custom");

            assertFalse(loader.hasExtension("custom"));
            assertThrows(IllegalStateException.class,
                    () -> loader.getExtension("custom"));
        }
    }

    // ==================== 动态扩展管理 ====================

    @Nested
    @DisplayName("动态扩展管理")
    class DynamicExtensionTests {

        @Test
        @DisplayName("从类路径注册动态扩展（sourceCode=null）")
        void registerDynamicExtension_fromClasspath() throws Exception {
            String className = "com.helianhealth.iflow.common.extension.fixture.DynamicTestExtension";

            loader.registerDynamicExtension("dynamic", className, null, false);

            assertTrue(loader.hasExtension("dynamic"));
            TestExtension ext = loader.getExtension("dynamic");
            assertEquals("dynamic:hello", ext.echo("hello"));
        }

        @Test
        @DisplayName("注册动态扩展后可被移除")
        void removeDynamicExtension_success() throws Exception {
            String className = "com.helianhealth.iflow.common.extension.fixture.DynamicTestExtension";
            loader.registerDynamicExtension("dynamic", className, null, false);
            assertTrue(loader.hasExtension("dynamic"));

            loader.removeDynamicExtension("dynamic");

            assertFalse(loader.hasExtension("dynamic"));
        }

        @Test
        @DisplayName("刷新动态扩展（卸载后重新加载）")
        void refreshDynamicExtension_success() throws Exception {
            String className = "com.helianhealth.iflow.common.extension.fixture.DynamicTestExtension";
            loader.registerDynamicExtension("dynamic", className, null, false);

            TestExtension before = loader.getExtension("dynamic");
            assertEquals("dynamic:x", before.echo("x"));

            // 刷新
            loader.refreshDynamicExtension("dynamic", className, null, false);

            TestExtension after = loader.getExtension("dynamic");
            assertEquals("dynamic:x", after.echo("x"));
            // 刷新后实例不是同一个
            assertNotSame(before, after);
        }

        /**
         * 模拟实际使用场景的复杂源码：包含多字段、多方法、条件逻辑、Map操作、异常处理
         */
        private static final String COMPLEX_SOURCE_CODE =
                "package com.helianhealth.iflow.common.extension.fixture;\n"
                + "\n"
                + "import java.util.HashMap;\n"
                + "import java.util.Map;\n"
                + "import java.util.stream.Collectors;\n"
                + "import java.util.Arrays;\n"
                + "\n"
                + "public class CompiledInvokerExtension implements TestExtension {\n"
                + "\n"
                + "    private static final String DEFAULT_CODE = \"200\";\n"
                + "    private static final Map<String, String> FIELD_MAPPING = new HashMap<>();\n"
                + "\n"
                + "    static {\n"
                + "        FIELD_MAPPING.put(\"patientName\", \"name\");\n"
                + "        FIELD_MAPPING.put(\"sex\", \"gender\");\n"
                + "        FIELD_MAPPING.put(\"identitycard\", \"idNo\");\n"
                + "        FIELD_MAPPING.put(\"phone\", \"tel\");\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    public String echo(String input) {\n"
                + "        if (input == null || input.trim().isEmpty()) {\n"
                + "            return buildResponse(\"400\", \"input is empty\");\n"
                + "        }\n"
                + "        try {\n"
                + "            String[] parts = input.split(\",\");\n"
                + "            String mapped = Arrays.stream(parts)\n"
                + "                    .map(this::mapField)\n"
                + "                    .collect(Collectors.joining(\",\"));\n"
                + "            return buildResponse(DEFAULT_CODE, mapped);\n"
                + "        } catch (Exception e) {\n"
                + "            return buildResponse(\"500\", \"error:\" + e.getMessage());\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    private String mapField(String field) {\n"
                + "        return FIELD_MAPPING.getOrDefault(field.trim(), field.trim());\n"
                + "    }\n"
                + "\n"
                + "    private String buildResponse(String code, String message) {\n"
                + "        return code + \":\" + message;\n"
                + "    }\n"
                + "}";

        /**
         * 带 @Slf4j Lombok 注解的源码，验证 DynamicCompiler 的 preprocessLombok 预处理
         */
        private static final String SLF4J_SOURCE_CODE =
                "package com.helianhealth.iflow.common.extension.fixture;\n"
                + "\n"
                + "import lombok.extern.slf4j.Slf4j;\n"
                + "\n"
                + "@Slf4j\n"
                + "public class Slf4jCompiledExtension implements TestExtension {\n"
                + "\n"
                + "    @Override\n"
                + "    public String echo(String input) {\n"
                + "        log.info(\"Processing input: {}\", input);\n"
                + "        return \"slf4j:\" + input;\n"
                + "    }\n"
                + "}";

        @Test
        @DisplayName("从源码动态编译注册复杂扩展（多字段、多方法、条件逻辑、Stream）")
        void registerDynamicExtension_complexSourceCode() throws Exception {
            String className = "com.helianhealth.iflow.common.extension.fixture.CompiledInvokerExtension";

            loader.registerDynamicExtension("compiled-invoker", className, COMPLEX_SOURCE_CODE, true);

            assertTrue(loader.hasExtension("compiled-invoker"));
            TestExtension ext = loader.getExtension("compiled-invoker");

            // 验证字段映射逻辑: patientName -> name, sex -> gender
            assertEquals("200:name,gender", ext.echo("patientName,sex"));
            // 验证未映射字段保持原值
            assertEquals("200:birthday", ext.echo("birthday"));
            // 验证空输入走 400 分支
            assertEquals("400:input is empty", ext.echo(""));
            assertEquals("400:input is empty", ext.echo(null));
        }

        @Test
        @DisplayName("带@Slf4j注解的源码动态编译（验证Lombok预处理）")
        void registerDynamicExtension_withSlf4jAnnotation() throws Exception {
            String className = "com.helianhealth.iflow.common.extension.fixture.Slf4jCompiledExtension";

            loader.registerDynamicExtension("slf4j-ext", className, SLF4J_SOURCE_CODE, true);

            assertTrue(loader.hasExtension("slf4j-ext"));
            TestExtension ext = loader.getExtension("slf4j-ext");
            assertEquals("slf4j:hello", ext.echo("hello"));
        }

        @Test
        @DisplayName("复杂扩展可被刷新（卸载v1重新编译v2）")
        void refreshDynamicExtension_complexSourceCode() throws Exception {
            String className = "com.helianhealth.iflow.common.extension.fixture.CompiledInvokerExtension";

            // v1: 用 COMPLEX_SOURCE_CODE 注册
            loader.registerDynamicExtension("compiled-invoker", className, COMPLEX_SOURCE_CODE, true);
            assertEquals("200:name", loader.getExtension("compiled-invoker").echo("patientName"));

            // v2: 换一套新的映射逻辑
            String sourceCodeV2 =
                    "package com.helianhealth.iflow.common.extension.fixture;\n"
                    + "import java.util.HashMap;\n"
                    + "import java.util.Map;\n"
                    + "public class CompiledInvokerExtension implements TestExtension {\n"
                    + "    private static final Map<String, String> MAPPING = new HashMap<>();\n"
                    + "    static { MAPPING.put(\"patientName\", \"fullName\"); }\n"
                    + "    @Override\n"
                    + "    public String echo(String input) {\n"
                    + "        return \"v2:\" + MAPPING.getOrDefault(input, input);\n"
                    + "    }\n"
                    + "}";

            loader.refreshDynamicExtension("compiled-invoker", className, sourceCodeV2, true);

            // 刷新后应走 v2 逻辑
            assertEquals("v2:fullName", loader.getExtension("compiled-invoker").echo("patientName"));
        }

        @Test
        @DisplayName("动态编译扩展可被移除并卸载ClassLoader")
        void removeDynamicExtension_compiled() throws Exception {
            String className = "com.helianhealth.iflow.common.extension.fixture.CompiledInvokerExtension";
            loader.registerDynamicExtension("compiled-invoker", className, COMPLEX_SOURCE_CODE, true);
            assertTrue(loader.hasExtension("compiled-invoker"));

            loader.removeDynamicExtension("compiled-invoker");

            assertFalse(loader.hasExtension("compiled-invoker"));
        }

        @Test
        @DisplayName("编译失败的源码抛出异常")
        void registerDynamicExtension_compileError() {
            String className = "com.helianhealth.iflow.common.extension.fixture.BadExtension";
            String badSourceCode = "package com.helianhealth.iflow.common.extension.fixture;\n"
                    + "public class BadExtension implements TestExtension {\n"
                    + "    // 缺少echo方法实现，编译报错\n"
                    + "}";

            assertThrows(Exception.class,
                    () -> loader.registerDynamicExtension("bad", className, badSourceCode, true));
            assertFalse(loader.hasExtension("bad"));
        }

        @Test
        @DisplayName("注册null名称抛出异常")
        void registerDynamicExtension_nullName() {
            assertThrows(IllegalArgumentException.class,
                    () -> loader.registerDynamicExtension(null, "SomeClass", null, false));
        }

        @Test
        @DisplayName("注册null类名抛出异常")
        void registerDynamicExtension_nullClassName() {
            assertThrows(IllegalArgumentException.class,
                    () -> loader.registerDynamicExtension("test", null, null, false));
        }
    }

    // ==================== Activate扩展 ====================

    @Nested
    @DisplayName("Activate扩展")
    class ActivateExtensionTests {

        @Test
        @DisplayName("无参数条件获取激活扩展（只返回无条件激活的）")
        void getActivateExtensions_noParams() {
            List<TestExtension> extensions = loader.getActivateExtensions();

            // high-priority 的 @Activate 没有 value 条件，无条件激活
            // activated 的 @Activate(value="token") 需要 token 参数才激活
            assertFalse(extensions.isEmpty());
            // 只有 high-priority 应该返回（无条件激活）
            assertEquals(1, extensions.size());
            assertEquals("high-priority:test", extensions.get(0).echo("test"));
        }

        @Test
        @DisplayName("带token参数获取激活扩展（条件匹配）")
        void getActivateExtensions_withTokenParam() {
            Map<String, String> params = new HashMap<>();
            params.put("token", "abc123");

            List<TestExtension> extensions = loader.getActivateExtensions(params);

            // high-priority(order=100) 和 activated(order=200) 都应该激活
            assertEquals(2, extensions.size());
            // 按order排序：high-priority(100) 在前，activated(200) 在后
            assertEquals("high-priority:test", extensions.get(0).echo("test"));
            assertEquals("activated:test", extensions.get(1).echo("test"));
        }

        @Test
        @DisplayName("不匹配的参数不激活条件扩展")
        void getActivateExtensions_noMatchingParam() {
            Map<String, String> params = new HashMap<>();
            params.put("other-key", "value");

            List<TestExtension> extensions = loader.getActivateExtensions(params);

            // 只有 high-priority 无条件激活
            assertEquals(1, extensions.size());
            assertEquals("high-priority:test", extensions.get(0).echo("test"));
        }
    }

    // ==================== clearCache ====================

    @Nested
    @DisplayName("clearCache")
    class ClearCacheTests {

        @Test
        @DisplayName("清除实例缓存后不再复用旧实例")
        void clearCache_instancesCleared() {
            // 先获取实例（缓存）
            TestExtension before = loader.getExtension("default");
            assertNotNull(before);
            // 缓存生效，同一实例
            assertSame(before, loader.getExtension("default"));

            // 清除所有缓存（包括cachedClasses和cachedInstances）
            loader.clearCache();

            // clearCache后重新获取会触发重新加载配置文件，返回新实例
            TestExtension after = loader.getExtension("default");
            assertNotNull(after);
            assertNotSame(before, after);
        }

        @Test
        @DisplayName("清除后重新添加扩展可正常工作")
        void clearCache_thenAddExtension() {
            loader.getExtension("default");

            loader.clearCache();

            // 手动添加回去后可以正常工作
            loader.addExtension("manual", DynamicTestExtension.class);
            TestExtension ext = loader.getExtension("manual");
            assertEquals("dynamic:test", ext.echo("test"));
        }
    }
}
