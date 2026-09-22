package com.helianhealth.iflow.common.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Agent Adaptive扩展注解
 * <p>
 * 用于标识自适应扩展类或方法：
 * 1. 标注在实现类上：表示该类是自定义的自适应扩展实现
 * 2. 标注在接口方法上：表示该方法需要自适应代理（根据运行时参数选择扩展）
 * <p>
 * 使用示例：
 * <pre>
 * // 方式1：标注在接口方法上，自动生成代理
 * {@code @AgentSPI("default")}
 * public interface MyService {
 *     {@code @AgentAdaptive}
 *     void process(String extensionName, Object data);
 * }
 *
 * // 方式2：标注在实现类上，使用自定义自适应逻辑
 * {@code @AgentAdaptive}
 * public class MyServiceAdaptive implements MyService {
 *     public void process(String extensionName, Object data) {
 *         // 自定义选择逻辑
 *     }
 * }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AgentAdaptive {

    /**
     * 用于从参数中提取扩展名的key
     *
     * 当标注在方法上时，会尝试从参数对象中按此顺序查找getter方法：
     * - getExtensionName()
     * - get{Value}() （如value="protocol"则找getProtocol()）
     *
     * @return 参数key数组，优先级从高到低
     */
    String[] value() default {};
}
