package com.helianhealth.agent.repository.mapper.agent;

import com.helianhealth.agent.repository.model.po.NodeExtensionConfigPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 节点扩展配置 Mapper
 */
@Mapper
public interface NodeExtensionConfigMapper {

    List<NodeExtensionConfigPO> selectByEnabled(@Param("enabled") Integer enabled);

    NodeExtensionConfigPO selectById(@Param("codeId") Integer codeId);

    NodeExtensionConfigPO selectBySpiInterfaceAndExtension(@Param("spiInterface") String spiInterface,
                                                           @Param("extensionName") String extensionName);

    void insert(NodeExtensionConfigPO record);

    void update(NodeExtensionConfigPO record);

    void deleteById(@Param("codeId") Integer codeId);

    List<NodeExtensionConfigPO> selectBySpiInterface(@Param("spiInterface") String spiInterface);
}
