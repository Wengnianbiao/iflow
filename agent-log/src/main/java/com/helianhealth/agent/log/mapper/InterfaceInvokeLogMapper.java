package com.helianhealth.agent.log.mapper;

import com.helianhealth.agent.log.model.InterfaceInvokeLogDO;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

public interface InterfaceInvokeLogMapper extends Mapper<InterfaceInvokeLogDO> {
    void insertLog(InterfaceInvokeLogDO logDO);

    List<InterfaceInvokeLogDO> selectAllInvokeLogs(@Param("nodeName") String nodeName, @Param("keyword") String keyword);
}