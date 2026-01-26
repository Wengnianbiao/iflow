package com.helianhealth.agent.common.extension;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent SPI机制单元测试
 */
class AgentExtensionLoaderTest {

    @BeforeEach
    void setUp() {
        // 清理状态
        ExtensionRegistry.getInstance().clear();
    }

    @AfterEach
    void tearDown() {
        ExtensionRegistry.getInstance().clear();
    }

    // ==================== 测试接口和实现类 ====================

    @AgentSPI("default")
    interface TestService {
        String hello(String name);
    }

    public static class DefaultTestService implements TestService {
        @Override
        public String hello(String name) {
            return "Default: Hello, " + name;
        }
    }

    public static class CustomTestService implements TestService {
        @Override
        public String hello(String name) {
            return "Custom: Hello, " + name;
        }
    }

    // 有参构造器的服务，用于测试IOC注入
    @AgentSPI
    interface DependencyService {
        String getDependencyName();
    }

    public static class SimpleDependencyService implements DependencyService {
        @Override
        public String getDependencyName() {
            return "SimpleDependency";
        }
    }

    public static class ServiceWithDependency implements TestService {
        private final DependencyService dependency;

        public ServiceWithDependency(DependencyService dependency) {
            this.dependency = dependency;
        }

        @Override
        public String hello(String name) {
            return "Injected[" + dependency.getDependencyName() + "]: Hello, " + name;
        }
    }

    // ==================== 测试用例 ====================

    @Test
    @DisplayName("测试获取ExtensionLoader")
    void testGetExtensionLoader() {
        AgentExtensionLoader<TestService> loader = 
            AgentExtensionLoader.getExtensionLoader(TestService.class);
        
        assertNotNull(loader);
        assertEquals(TestService.class, loader.getType());
    }

    @Test
    @DisplayName("测试非接口类型应抛出异常")
    void testNonInterfaceTypeShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> 
            AgentExtensionLoader.getExtensionLoader(String.class));
    }

    @Test
    @DisplayName("测试无@AgentSPI注解应抛出异常")
    void testWithoutAnnotationShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> 
            AgentExtensionLoader.getExtensionLoader(Runnable.class));
    }

    @Test
    @DisplayName("测试动态注册和获取扩展")
    void testAddAndGetExtension() {
        AgentExtensionLoader<TestService> loader = 
            AgentExtensionLoader.getExtensionLoader(TestService.class);
        
        // 注册扩展
        loader.addExtension("default", DefaultTestService.class);
        loader.addExtension("custom", CustomTestService.class);
        
        // 获取扩展
        TestService defaultService = loader.getExtension("default");
        TestService customService = loader.getExtension("custom");
        
        assertNotNull(defaultService);
        assertNotNull(customService);
        assertEquals("Default: Hello, World", defaultService.hello("World"));
        assertEquals("Custom: Hello, World", customService.hello("World"));
    }

    @Test
    @DisplayName("测试默认扩展名")
    void testDefaultExtension() {
        AgentExtensionLoader<TestService> loader = 
            AgentExtensionLoader.getExtensionLoader(TestService.class);
        
        assertEquals("default", loader.getDefaultExtensionName());
        
        // 注册默认扩展
        loader.addExtension("default", DefaultTestService.class);
        
        // 获取默认扩展
        TestService service = loader.getDefaultExtension();
        assertNotNull(service);
        assertEquals("Default: Hello, Test", service.hello("Test"));
    }

    @Test
    @DisplayName("测试扩展单例")
    void testExtensionSingleton() {
        AgentExtensionLoader<TestService> loader = 
            AgentExtensionLoader.getExtensionLoader(TestService.class);
        
        loader.addExtension("singleton", DefaultTestService.class);
        
        TestService first = loader.getExtension("singleton");
        TestService second = loader.getExtension("singleton");
        
        assertSame(first, second, "应该返回同一个实例");
    }

    @Test
    @DisplayName("测试hasExtension")
    void testHasExtension() {
        AgentExtensionLoader<TestService> loader = 
            AgentExtensionLoader.getExtensionLoader(TestService.class);
        
        assertFalse(loader.hasExtension("test"));
        
        loader.addExtension("test", DefaultTestService.class);
        
        assertTrue(loader.hasExtension("test"));
    }

    @Test
    @DisplayName("测试getSupportedExtensions")
    void testGetSupportedExtensions() {
        AgentExtensionLoader<TestService> loader = 
            AgentExtensionLoader.getExtensionLoader(TestService.class);
        
        loader.addExtension("ext1", DefaultTestService.class);
        loader.addExtension("ext2", CustomTestService.class);
        
        java.util.Set<String> extensions = loader.getSupportedExtensions();
        
        assertEquals(2, extensions.size());
        assertTrue(extensions.contains("ext1"));
        assertTrue(extensions.contains("ext2"));
    }

    @Test
    @DisplayName("测试替换扩展")
    void testReplaceExtension() {
        AgentExtensionLoader<TestService> loader = 
            AgentExtensionLoader.getExtensionLoader(TestService.class);
        
        loader.addExtension("replaceable", DefaultTestService.class);
        
        TestService before = loader.getExtension("replaceable");
        assertEquals("Default: Hello, Test", before.hello("Test"));
        
        // 替换扩展
        loader.replaceExtension("replaceable", CustomTestService.class);
        
        // 清除缓存后重新获取
        loader.clearCache();
        TestService after = loader.getExtension("replaceable");
        assertEquals("Custom: Hello, Test", after.hello("Test"));
    }

    @Test
    @DisplayName("测试移除扩展")
    void testRemoveExtension() {
        AgentExtensionLoader<TestService> loader = 
            AgentExtensionLoader.getExtensionLoader(TestService.class);
        
        loader.addExtension("removable", DefaultTestService.class);
        assertTrue(loader.hasExtension("removable"));
        
        loader.removeExtension("removable");
        assertFalse(loader.hasExtension("removable"));
    }

    @Test
    @DisplayName("测试重复添加扩展应抛出异常")
    void testDuplicateAddShouldThrow() {
        AgentExtensionLoader<TestService> loader = 
            AgentExtensionLoader.getExtensionLoader(TestService.class);
        
        loader.addExtension("dup", DefaultTestService.class);
        
        assertThrows(IllegalStateException.class, () -> 
            loader.addExtension("dup", CustomTestService.class));
    }

    @Test
    @DisplayName("测试动态编译扩展")
    void testDynamicCompilation() throws Exception {
        String sourceCode = 
            "package com.helianhealth.test;\n" +
            "\n" +
            "import com.helianhealth.agent.common.extension.AgentExtensionLoaderTest.TestService;\n" +
            "\n" +
            "public class DynamicTestService implements TestService {\n" +
            "    @Override\n" +
            "    public String hello(String name) {\n" +
            "        return \"Dynamic: Hello, \" + name;\n" +
            "    }\n" +
            "}\n";
        
        ExtensionRegistry registry = ExtensionRegistry.getInstance();
        
        // 使用动态编译加载
        registry.loadAndRegister(
            TestService.class.getName(),
            "dynamic",
            "com.helianhealth.test.DynamicTestService",
            sourceCode
        );
        
        // 获取并验证
        AgentExtensionLoader<TestService> loader = 
            AgentExtensionLoader.getExtensionLoader(TestService.class);
        
        TestService dynamicService = loader.getExtension("dynamic");
        assertNotNull(dynamicService);
        assertEquals("Dynamic: Hello, World", dynamicService.hello("World"));
    }
}
