package com.helianhealth.agent.console.controller.request;

import lombok.Data;

import java.util.List;

/**
 * 节点扩展配置创建请求对象
 */
@Data
public class NodeExtensionConfigCreateReq {

    /**
     * 绑定的流程节点 ID 列表（JSON 数组；与库表 binding_ids 一致）
     * <ul>
     * <li>RemoteInvoker扩展：单节点绑定，如 {@code [1]}，自动更新节点的 remote_extension 字段</li>
     * <li>Filter扩展：多节点绑定，如 {@code [1,2,3]}，自动更新节点的 extension_param 字段</li>
     * </ul>
     */
    private List<Integer> bindingIds;

    /**
     * SPI接口（对应SPI接口的全限定名），例如: InterfaceRemoteInvoker, InterfaceClientProxy等
     */
    private String spiInterface;

    /**
     * 扩展名称（用于SPI配置文件中的key），例如: pacs-result等
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
     * 扩展方式（必选）：PROJECT-项目扩展 | ONLINE-在线扩展
     * <ul>
     * <li><b>PROJECT</b>: 项目扩展 - 在业务项目中实现接口，适合复杂业务逻辑，可引入依赖、使用Spring注入</li>
     * <li><b>ONLINE</b>: 在线扩展 - 在管理页面编写代码，适合简单快速实现，无需重启应用，适用于调试场景</li>
     * </ul>
     */
    private String extensionMode;

    /**
     * 是否启用 1-启用 0-禁用
     */
    private Integer enabled;

    /**
     * 扩展参数值
     * 用于存储扩展执行时需要的参数配置
     * 用于拓展spi为Filter的扩展方式
     */
    private String extensionParamValue;

    /**
     * 仅 OpenApiRouteHandler / JarvisRouteHandler：保存后要绑定的工作流 ID，
     * 会写入该工作流 {@code route_extension} 为当前扩展名，并在注册/更新时与 SPI 扩展一并生效。
     */
    private Integer bindFlowId;
}
