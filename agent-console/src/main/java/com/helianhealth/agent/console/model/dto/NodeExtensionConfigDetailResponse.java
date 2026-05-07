package com.helianhealth.agent.console.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 节点扩展配置详情响应对象
 */
@Data
public class NodeExtensionConfigDetailResponse {

    /**
     * 主键（config_id）
     */
    private Integer configId;

    /**
     * 绑定的流程节点 ID 列表（binding_ids）
     */
    private List<Integer> bindingIds;

    /**
     * SPI接口
     */
    private String spiInterface;

    /**
     * 扩展名称
     */
    private String extensionName;

    /**
     * 类的全限定名
     */
    private String className;

    /**
     * Java源代码
     */
    private String sourceCode;

    /**
     * 代码描述
     */
    private String description;

    /**
     * 扩展方式：PROJECT-项目扩展 | ONLINE-在线扩展
     */
    private String extensionMode;

    /**
     * 是否启用 1-启用 0-禁用
     */
    private Integer enabled;

    /**
     * 扩展参数值（从节点提取，用于编辑回显）
     */
    private String extensionParamValue;
}
