package com.helianhealth.iflow.core.utils;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 任务 / 工作流实例 ID 生成器
 */
@Component
public class IDGenerator {

    public String generate() {
        return UUID.randomUUID().toString();
    }
}
