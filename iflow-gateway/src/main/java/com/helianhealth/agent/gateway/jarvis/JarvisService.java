package com.helianhealth.agent.gateway.jarvis;

import com.helianhealth.agent.gateway.jarvis.model.JarvisRequest;
import com.helianhealth.agent.gateway.jarvis.model.JarvisResultData;

/**
 * Jarvis 入站服务。
 */
public interface JarvisService {

    JarvisResultData<Object> invoke(JarvisRequest request);
}
