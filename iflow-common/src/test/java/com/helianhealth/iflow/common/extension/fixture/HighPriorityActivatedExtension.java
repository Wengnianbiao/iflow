package com.helianhealth.iflow.common.extension.fixture;

import com.helianhealth.iflow.common.extension.Activate;

/**
 * 高优先级激活扩展（无条件激活，order=100，排在ActivatedTestExtension前面）
 */
@Activate(order = 100)
public class HighPriorityActivatedExtension implements TestExtension {
    @Override
    public String echo(String input) {
        return "high-priority:" + input;
    }
}
