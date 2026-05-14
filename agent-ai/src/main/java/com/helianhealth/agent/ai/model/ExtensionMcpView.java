package com.helianhealth.agent.ai.model;

import lombok.Data;
import java.util.List;

/**
 * MCP接口 - 扩展配置视图
 */
@Data
public class ExtensionMcpView {
    private Integer configId;
    private List<Integer> bindingIds;
    private String spiInterface;
    private String extensionName;
    private String className;
    private String sourceCode;
    private String description;
    private String extensionMode;
    private Integer enabled;
    private String extensionParamValue;
}
