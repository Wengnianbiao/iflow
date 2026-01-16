package com.helianhealth.extension;

import com.helianhealth.extension.spi.TestSpi;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * TestSpi 测试实现类
 */
@Slf4j
public class TestInvoker implements TestSpi {

    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        log.info("测试SPI拓展！！input: {}", input);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "success");
        result.put("data", "test data");
        return result;
    }
}
