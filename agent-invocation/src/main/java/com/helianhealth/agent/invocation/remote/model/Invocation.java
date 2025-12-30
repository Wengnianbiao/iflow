package com.helianhealth.agent.invocation.remote.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
/**
 * 远程调用上下文
 * 封装调用所需的参数配置和业务数据
 */
@Data
@Builder
public class Invocation {

    /**
     * 前置参数配置列表（请求参数映射规则）
     */
    private List<NodeParamConfig> preParamConfigs;

    /**
     * 后置参数配置列表（响应参数映射规则）
     */
    private List<NodeParamConfig> postParamConfigs;

    /**
     * 业务数据
     */
    private Map<String, Object> businessData;
}
