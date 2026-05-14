package com.helianhealth.agent.ai.model;

import lombok.Data;
import java.util.List;

/**
 * MCP接口 - 更新扩展请求
 */
@Data
public class ExtensionUpdateMcpRequest {
    private Integer configId;
    private List<Integer> bindingIds;
    private String extensionName;
    private String className;
    private String sourceCode;
    private String description;
    private String extensionMode;
    private Integer enabled;
    private String extensionParamValue;
}
