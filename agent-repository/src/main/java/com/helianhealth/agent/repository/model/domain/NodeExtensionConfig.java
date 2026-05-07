package com.helianhealth.agent.repository.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SPI 扩展配置领域模型（对应表 spi_extension_config）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeExtensionConfig {

    private Integer configId;

    /**
     * 绑定的流程节点 ID 列表（远程调用 / 过滤器等作用域；JSON 存库）
     */
    private List<Integer> bindingIds;

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
