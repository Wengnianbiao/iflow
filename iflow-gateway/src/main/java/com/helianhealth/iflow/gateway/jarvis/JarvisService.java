package com.helianhealth.iflow.gateway.jarvis;

import java.util.Map;

/**
 * Jarvis 入站服务。
 */
public interface JarvisService {

    /**
     * 成功时返回工作流原始输出，失败时返回 Jarvis 错误对象。
     */
    Object invoke(Map<String, Object> input);
}
