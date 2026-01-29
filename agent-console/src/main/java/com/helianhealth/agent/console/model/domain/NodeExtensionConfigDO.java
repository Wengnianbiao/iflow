package com.helianhealth.agent.console.model.domain;

import com.helianhealth.agent.common.typehandler.ListIntegerTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;
import tk.mybatis.mapper.annotation.ColumnType;

import javax.persistence.*;
import java.util.List;

/**
 * 节点扩展配置领域对象
 * 对动态 SPI 节点实现的配置（包括类名、源码等）
 */
@Data
@Entity
@Table(name = "node_extension_config")
public class NodeExtensionConfigDO {

    /**
     * 主键ID
     */
    @Id
    @Column(name = "code_id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer codeId;

    /**
     * 关联的节点ID（如 "[1,2,3]"）
     * <ul>
     * <li>RemoteInvoker扩展：单节点绑定，如 "[1]"，自动更新节点的 remote_extension 字段</li>
     * <li>Filter扩展：多节点绑定，如 "[1,2,3]"，自动更新节点的 extension_param 字段</li>
     * </ul>
     */
    @Column(name = "node_id")
    @ColumnType(typeHandler = ListIntegerTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private List<Integer> nodeId;

    /**
     * SPI接口（对应SPI接口的全限定名），例如: InterfaceRemoteInvoker, InterfaceClientProxy等
     */
    @Column(name = "spi_interface")
    private String spiInterface;

    /**
     * 扩展名称（用于SPI配置文件中的key），例如: pacs-result等
     */
    @Column(name = "extension_name")
    private String extensionName;

    /**
     * 类的全限定名，例如: com.helianhealth.agent.remote.custom.CustomHttpClientProxy
     */
    @Column(name = "class_name")
    private String className;

    /**
     * Java源代码
     */
    @Column(name = "source_code", columnDefinition = "TEXT")
    private String sourceCode;

    /**
     * 代码描述
     */
    @Column(name = "description")
    private String description;

    /**
     * 扩展方式（必选）：PROJECT-项目扩展 | ONLINE-在线扩展
     * <ul>
     * <li><b>PROJECT</b>: 项目扩展 - 在业务项目中实现接口，适合复杂业务逻辑，可引入依赖、使用Spring注入</li>
     * <li><b>ONLINE</b>: 在线扩展 - 在管理页面编写代码，适合简单快速实现，无需重启应用</li>
     * </ul>
     */
    @Column(name = "extension_mode")
    private String extensionMode;

    /**
     * 是否启用 1-启用 0-禁用
     */
    @Column(name = "enabled")
    private Integer enabled;
}
