package com.helianhealth.agent.workflow.mapper.agent;

import com.helianhealth.agent.workflow.model.domain.NodeExtensionConfigDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 节点扩展配置 Mapper
 */
@Mapper
public interface NodeExtensionConfigMapper {

    List<NodeExtensionConfigDO> selectByEnabled(@Param("enabled") Integer enabled);

    NodeExtensionConfigDO selectById(@Param("codeId") Integer codeId);

    NodeExtensionConfigDO selectBySpiInterfaceAndExtension(@Param("spiInterface") String spiInterface,
                                                           @Param("extensionName") String extensionName);

    void insert(NodeExtensionConfigDO record);

    void update(NodeExtensionConfigDO record);

    void deleteById(@Param("codeId") Integer codeId);

    void deleteBySpiInterfaceAndExtension(@Param("spiInterface") String spiInterface,
                                          @Param("extensionName") String extensionName);
}
