package com.helianhealth.iflow.common.extension.fixture;

import com.helianhealth.iflow.common.extension.AgentActivate;

/**
 * 带激活条件的测试扩展（需要 token 参数，order=200）
 */
@AgentActivate(value = "token", order = 200)
public class ActivatedTestExtension implements TestExtension {
    @Override
    public String echo(String input) {
        return "activated:" + input;
    }
}
