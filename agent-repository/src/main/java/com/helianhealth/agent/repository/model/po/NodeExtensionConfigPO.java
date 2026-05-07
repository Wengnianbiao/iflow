package com.helianhealth.agent.repository.model.po;

import com.helianhealth.agent.common.typehandler.ListIntegerTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;
import tk.mybatis.mapper.annotation.ColumnType;

import javax.persistence.*;
import java.util.List;

/**
 * SPI 扩展配置持久化对象
 * 对动态 SPI 节点实现的配置（包括类名、源码等）
 */
@Data
@Entity
@Table(name = "spi_extension_config")
public class NodeExtensionConfigPO {

    @Id
    @Column(name = "config_id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer configId;

    @Column(name = "binding_ids")
    @ColumnType(typeHandler = ListIntegerTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private List<Integer> bindingIds;

    @Column(name = "spi_interface")
    private String spiInterface;

    @Column(name = "extension_name")
    private String extensionName;

    @Column(name = "class_name")
    private String className;

    @Column(name = "source_code", columnDefinition = "TEXT")
    private String sourceCode;

    @Column(name = "description")
    private String description;

    @Column(name = "extension_mode")
    private String extensionMode;

    @Column(name = "enabled")
    private Integer enabled;

    @Column(name = "extension_param_value", columnDefinition = "TEXT")
    private String extensionParamValue;
}
