package com.helianhealth.agent.invocation.remote.param.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 后置参数树构建上下文
 * 用于封装后置参数转换所需的所有数据源（输入 + 响应）
 */
@Data
@Builder
public class PostBuildContext {
    
    /**
     * 当前层输入数据
     */
    private Map<String, Object> currentInputData;
    
    /**
     * 当前层响应数据
     */
    private Map<String, Object> currentResponseData;
    
    /**
     * 根输入数据（用于绝对路径取值原始输入）
     */
    private Map<String, Object> rootInputData;
    
    /**
     * 根响应数据（用于绝对路径取值响应结果）
     */
    private Map<String, Object> rootResponseData;
}
