package com.helianhealth.extension.spi;

import org.apache.dubbo.common.extension.SPI;

import java.util.Map;

/**
 * 测试用 SPI 接口
 * 用于验证动态 SPI 加载功能
 */
@SPI
public interface TestSpi {

    /**
     * 执行测试方法
     *
     * @param input 输入参数
     * @return 执行结果
     */
    Map<String, Object> execute(Map<String, Object> input);
}
