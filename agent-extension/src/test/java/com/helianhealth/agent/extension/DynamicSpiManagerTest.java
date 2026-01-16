package com.helianhealth.agent.extension;

import com.helianhealth.agent.extension.spi.dynamic.DynamicSpiManager;
import com.helianhealth.agent.extension.spi.model.DynamicSpiDefinition;
import com.helianhealth.extension.spi.TestSpi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DynamicSpiManager 测试类
 * 测试 loadAndRegister 方法的两种加载模式：
 * 1. 从 classpath 加载预编译的类（项目模式）
 * 2. 动态编译源码并加载（动态编译模式）
 */
public class DynamicSpiManagerTest {

    private DynamicSpiManager dynamicSpiManager;
    
    @BeforeEach
    public void setUp() {
        dynamicSpiManager = new DynamicSpiManager();
    }
    
    @AfterEach
    public void tearDown() {
        dynamicSpiManager.clear();
    }
    
    /**
     * 测试：从 classpath 加载预编译的类（项目模式）
     */
    @Test
    public void testLoadFromClasspath() throws Exception {
        // 创建 SPI 定义（sourceCode 为空，使用 classpath 模式）
        DynamicSpiDefinition definition = new DynamicSpiDefinition();
        definition.setSpiInterface("com.helianhealth.extension.spi.TestSpi");
        definition.setExtensionName("testInvoker");
        definition.setClassName("com.helianhealth.extension.TestInvoker");
        definition.setSourceCode(null); // 关键：为空则从 classpath 加载
        
        // 执行加载和注册
        dynamicSpiManager.loadAndRegister(definition);
        
        // 验证：检查是否已注册
        boolean hasExtension = dynamicSpiManager.hasExtension(
            "com.helianhealth.extension.spi.TestSpi",
            "testInvoker"
        );
        assertTrue(hasExtension, "扩展应该已注册");
        
        // 验证：获取扩展实例并调用
        TestSpi testSpi = dynamicSpiManager.getExtension(
            "com.helianhealth.extension.spi.TestSpi",
            "testInvoker"
        );
        assertNotNull(testSpi);
        
        // 测试调用
        Map<String, Object> input = new HashMap<>();
        input.put("testKey", "testValue");
        
        Map<String, Object> result = testSpi.execute(input);
        assertEquals("200", result.get("code"));
        assertEquals("success", result.get("message"));
        assertEquals("test data", result.get("data"));
    }
    
    /**
     * 测试：动态编译源码（动态编译模式）
     */
    @Test
    public void testLoadFromSourceCode() throws Exception {
        // 准备源代码
        String sourceCode = "package com.helianhealth.extension.dynamic;\n" +
            "\n" +
            "import com.helianhealth.extension.spi.TestSpi;\n" +
            "\n" +
            "import java.util.HashMap;\n" +
            "import java.util.Map;\n" +
            "\n" +
            "public class DynamicTestInvoker implements TestSpi {\n" +
            "    @Override\n" +
            "    public Map<String, Object> execute(Map<String, Object> input) {\n" +
            "        Map<String, Object> result = new HashMap<>();\n" +
            "        result.put(\"code\", \"200\");\n" +
            "        result.put(\"message\", \"dynamic compiled success\");\n" +
            "        result.put(\"input\", input);\n" +
            "        return result;\n" +
            "    }\n" +
            "}";
        
        // 创建 SPI 定义（sourceCode 不为空，使用动态编译模式）
        DynamicSpiDefinition definition = new DynamicSpiDefinition();
        definition.setSpiInterface("com.helianhealth.extension.spi.TestSpi");
        definition.setExtensionName("dynamicTestInvoker");
        definition.setClassName("com.helianhealth.extension.dynamic.DynamicTestInvoker");
        definition.setSourceCode(sourceCode); // 关键：提供源码则动态编译
        
        // 执行加载和注册
        dynamicSpiManager.loadAndRegister(definition);
        
        // 验证：检查是否已注册
        boolean hasExtension = dynamicSpiManager.hasExtension(
            "com.helianhealth.extension.spi.TestSpi",
            "dynamicTestInvoker"
        );
        assertTrue(hasExtension, "动态编译的扩展应该已注册");
        
        // 验证：获取扩展实例并调用
        TestSpi testSpi = dynamicSpiManager.getExtension(
            "com.helianhealth.extension.spi.TestSpi",
            "dynamicTestInvoker"
        );
        assertNotNull(testSpi);
        
        // 测试调用
        Map<String, Object> input = new HashMap<>();
        input.put("dynamicKey", "dynamicValue");
        
        Map<String, Object> result = testSpi.execute(input);
        assertEquals("200", result.get("code"));
        assertEquals("dynamic compiled success", result.get("message"));
    }
    
    /**
     * 测试：刷新扩展
     */
    @Test
    public void testRefreshExtension() throws Exception {
        // 先加载一个扩展
        DynamicSpiDefinition definition = new DynamicSpiDefinition();
        definition.setSpiInterface("com.helianhealth.extension.spi.TestSpi");
        definition.setExtensionName("testInvokerRefresh");
        definition.setClassName("com.helianhealth.extension.TestInvoker");
        definition.setSourceCode(null);
        
        dynamicSpiManager.loadAndRegister(definition);
        
        // 刷新扩展
        dynamicSpiManager.refresh(definition);
        
        // 验证：刷新后扩展仍然存在
        boolean hasExtension = dynamicSpiManager.hasExtension(
            "com.helianhealth.extension.spi.TestSpi",
            "testInvokerRefresh"
        );
        assertTrue(hasExtension, "刷新后扩展应该仍然存在");
    }
    
    /**
     * 测试：移除扩展
     */
    @Test
    public void testRemoveExtension() throws Exception {
        // 先加载一个扩展
        DynamicSpiDefinition definition = new DynamicSpiDefinition();
        definition.setSpiInterface("com.helianhealth.extension.spi.TestSpi");
        definition.setExtensionName("testInvokerRemove");
        definition.setClassName("com.helianhealth.extension.TestInvoker");
        definition.setSourceCode(null);
        
        dynamicSpiManager.loadAndRegister(definition);
        
        // 验证：扩展已注册
        assertTrue(dynamicSpiManager.hasExtension(
            "com.helianhealth.extension.spi.TestSpi",
            "testInvokerRemove"
        ));
        
        // 移除扩展
        dynamicSpiManager.remove(
            "com.helianhealth.extension.spi.TestSpi",
            "testInvokerRemove"
        );
        
        // 验证：扩展已不存在
        assertFalse(dynamicSpiManager.hasExtension(
            "com.helianhealth.extension.spi.TestSpi",
            "testInvokerRemove"
        ), "移除后扩展应该不存在");
    }
    
    /**
     * 测试：获取所有已注册扩展
     */
    @Test
    public void testGetAllRegisteredExtensions() throws Exception {
        // 加载多个扩展
        DynamicSpiDefinition definition1 = new DynamicSpiDefinition();
        definition1.setSpiInterface("com.helianhealth.extension.spi.TestSpi");
        definition1.setExtensionName("testInvoker1");
        definition1.setClassName("com.helianhealth.extension.TestInvoker");
        definition1.setSourceCode(null);
        
        dynamicSpiManager.loadAndRegister(definition1);
        
        // 验证：获取所有注册的扩展
        Map<String, Class<?>> allExtensions = dynamicSpiManager.getAllRegisteredExtensions();
        assertEquals(1, allExtensions.size(), "应该有1个已注册的扩展");
        assertTrue(allExtensions.containsKey(
            "com.helianhealth.extension.spi.TestSpi#testInvoker1"
        ));
    }
}
