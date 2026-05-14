package com.helianhealth.agent.ai.model;

import lombok.Data;
import java.util.List;

/**
 * MCP接口 - 注册扩展请求
 */
@Data
public class ExtensionRegisterMcpRequest {
    private List<Integer> bindingIds;
    private String extensionName;
    private String className;
    private String sourceCode;
    private String description;
    private String extensionMode;
    private Integer enabled;
    private String extensionParamValue;
}
