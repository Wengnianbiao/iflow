package com.helianhealth.agent.common.extension.fixture;

/**
 * 动态注册测试用扩展（不在配置文件中，通过registerDynamicExtension从类路径加载）
 */
public class DynamicTestExtension implements TestExtension {
    @Override
    public String echo(String input) {
        return "dynamic:" + input;
    }
}
