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

    /** 根据启用状态查询 */
    List<NodeExtensionConfigPO> selectByEnabled(@Param("enabled") Integer enabled);

    /** 根据ID查询 */
    NodeExtensionConfigPO selectById(Integer configId);

    /** 根据SPI接口和扩展名称查询 */
    NodeExtensionConfigPO selectBySpiInterfaceAndExtension(@Param("spiInterface") String spiInterface, @Param("extensionName") String extensionName);

    /** 插入新记录 */
    int insert(NodeExtensionConfigPO config);

    /** 更新记录 */
    int update(NodeExtensionConfigPO config);

    /** 根据ID删除 */
    int deleteById(Integer configId);

    /** 根据SPI接口查询所有配置 */
    List<NodeExtensionConfigPO> selectBySpiInterface(@Param("spiInterface") String spiInterface);
}
