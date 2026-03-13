package com.helianhealth.agent.repository.mapper.agent;

import com.helianhealth.agent.repository.model.po.InterfaceInvokeLogDO;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.Date;
import java.util.List;

public interface InterfaceInvokeLogMapper extends Mapper<InterfaceInvokeLogDO> {
    void insertLog(InterfaceInvokeLogDO logDO);

    List<InterfaceInvokeLogDO> selectAllInvokeLogs(@Param("nodeName") String nodeName, @Param("keyword") String keyword);

    List<InterfaceInvokeLogDO> selectLogsByTimeRange(@Param("startTime") Date startTime);

    /**
     * 删除指定时间之前的日志
     */
    int deleteLogsBefore(@Param("beforeTime") Date beforeTime);
}
