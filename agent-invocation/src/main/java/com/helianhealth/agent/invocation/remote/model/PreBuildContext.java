package com.helianhealth.agent.invocation.remote.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 前置参数树构建上下文
 * 用于封装前置参数转换所需的所有数据源
 */
@Data
@Builder
public class PreBuildContext {
    
    /**
     * 当前层业务数据
     */
    private Map<String, Object> currentData;
    
    /**
     * 父层业务数据（用于嵌套对象/数组的相对路径取值）
     */
    private Map<String, Object> parentData;
    
    /**
     * 根业务数据（用于绝对路径取值，通常是最顶层的输入数据）
     */
    private Map<String, Object> rootData;
}
