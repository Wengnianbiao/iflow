package com.helianhealth.agent.invocation.remote.dynamic;

import com.helianhealth.agent.extension.spi.dynamic.DynamicClassLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 动态类加载器测试
 * 这里只验证动态编译与异常处理逻辑，具体 SPI 管理在 agent-extension 模块中测试
 */
public class DynamicClassLoaderTest {

    @Test
    public void testLoadSimpleClass() throws Exception {
        String className = "com.test.HelloWorld";
        String sourceCode =
            "package com.test;\n" +
            "\n" +
            "public class HelloWorld {\n" +
            "    public String sayHello() {\n" +
            "        return \"Hello from dynamic class!\";\n" +
            "    }\n" +
            "}";

        DynamicClassLoader loader = new DynamicClassLoader(
            Thread.currentThread().getContextClassLoader()
        );

        Class<?> clazz = loader.loadClass(className, sourceCode);
        assertEquals(className, clazz.getName());

        Object instance = clazz.newInstance();
        Object result = clazz.getMethod("sayHello").invoke(instance);
        assertEquals("Hello from dynamic class!", result);
    }

    @Test
    public void testCompilationError() {
        String className = "com.test.InvalidClass";
        String sourceCode =
            "package com.test;\n" +
            "\n" +
            "public class InvalidClass {\n" +
            "    public void method() {\n" +
            "        // 故意制造编译错误\n" +
            "        invalid syntax here\n" +
            "    }\n" +
            "}";

        DynamicClassLoader loader = new DynamicClassLoader(
            Thread.currentThread().getContextClassLoader()
        );

        try {
            loader.loadClass(className, sourceCode);
            fail("Should throw compilation exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Compilation failed"));
        }
    }
}
