package com.helianhealth.agent.repository.mapper;

import com.helianhealth.agent.repository.model.po.InterfaceInvokeLogDO;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.Date;
import java.util.List;

public interface InterfaceInvokeLogMapper extends Mapper<InterfaceInvokeLogDO> {
    void insertLog(InterfaceInvokeLogDO logDO);

    List<InterfaceInvokeLogDO> selectAllInvokeLogs(@Param("nodeName") String nodeName, @Param("keyword") String keyword);

    List<InterfaceInvokeLogDO> selectLogsByTimeRange(@Param("startTime") Date startTime);
}
