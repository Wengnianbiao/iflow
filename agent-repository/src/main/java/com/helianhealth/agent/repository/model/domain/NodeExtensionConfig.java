package com.helianhealth.agent.repository.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 节点扩展配置领域模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeExtensionConfig {

    private Integer codeId;

    private List<Integer> nodeId;

    private String spiInterface;

    private String extensionName;

    private String className;

    private String sourceCode;

    private String description;

    private String extensionMode;

    private Integer enabled;

    private String extensionParamValue;

    public boolean isEnabled() {
        return Integer.valueOf(1).equals(this.enabled);
    }

}
