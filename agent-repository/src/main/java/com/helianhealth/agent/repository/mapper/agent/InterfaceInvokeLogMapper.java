package com.helianhealth.agent.repository.mapper.agent;

import com.helianhealth.agent.repository.model.po.InterfaceInvokeLogPO;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.Date;
import java.util.List;

public interface InterfaceInvokeLogMapper {
    void insertLog(InterfaceInvokeLogPO logDO);

    List<InterfaceInvokeLogPO> selectAllInvokeLogs(@Param("nodeName") String nodeName, @Param("keyword") String keyword);

    List<InterfaceInvokeLogPO> selectLogsByTimeRange(@Param("startTime") Date startTime);

    /**
     * 删除指定时间之前的日志
     */
    int deleteLogsBefore(@Param("beforeTime") Date beforeTime);
}
