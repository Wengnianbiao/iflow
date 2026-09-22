package com.helianhealth.iflow.common.extension.fixture;

import com.helianhealth.iflow.common.extension.AgentSPI;

/**
 * 测试用SPI接口
 */
@AgentSPI("default")
public interface TestExtension {

    String echo(String input);
}
