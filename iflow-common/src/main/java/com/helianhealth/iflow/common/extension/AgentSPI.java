package com.helianhealth.iflow.common.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Agent SPI扩展点标记注解
 * <p>
 * 用于标识一个接口为SPI扩展点，支持通过配置文件或动态注册的方式加载实现类。
 * <p>
 * 使用方式：
 * <pre>
 * {@code @AgentSPI("default")}
 * public interface MyExtension {
 *     void doSomething();
 * }
 * </pre>
 *
 * 配置文件位置：META-INF/agent/{接口全限定名}
 * 配置文件格式：
 * <pre>
 * extensionName=com.example.ExtensionImpl
 * </pre>
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AgentSPI {

    /**
     * 默认扩展名
     * 当调用 {@link AgentExtensionLoader#getDefaultExtension()} 时，
     * 会使用此值作为扩展名获取对应实现
     *
     * @return 默认扩展名，空字符串表示无默认值
     */
    String value() default "";
}
