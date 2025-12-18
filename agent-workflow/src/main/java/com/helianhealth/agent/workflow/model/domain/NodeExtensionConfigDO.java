package com.helianhealth.agent.workflow.model.domain;

import lombok.Data;

/**
 * 节点扩展配置领域对象
 * 对动态 SPI 节点实现的配置（包括类名、源码等）
 */
@Data
public class NodeExtensionConfigDO {

    /**
     * 主键ID
     */
    private Integer codeId;

    /**
     * 关联的节点ID（用于自动配置节点的remote_extension字段）
     */
    private Integer nodeId;

    /**
     * SPI接口（对应SPI接口的全限定名），例如: InterfaceRemoteInvoker, InterfaceClientProxy等
     */
    private String spiInterface;

    /**
     * 扩展名称（用于SPI配置文件中的key），例如: custom-http, custom-param-builder等
     */
    private String extensionName;

    /**
     * 类的全限定名，例如: com.helianhealth.agent.remote.custom.CustomHttpClientProxy
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
     * 是否启用 1-启用 0-禁用
     */
    private Integer enabled;
}
