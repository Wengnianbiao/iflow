package com.helianhealth.iflow.common.extension.fixture;

import com.helianhealth.iflow.common.extension.SPI;

/**
 * 测试用SPI接口
 */
@SPI("default")
public interface TestExtension {

    String echo(String input);
}
