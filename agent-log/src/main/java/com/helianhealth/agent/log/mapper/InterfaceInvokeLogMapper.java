package com.helianhealth.agent.log.mapper;

import com.helianhealth.agent.log.model.InterfaceInvokeLogDO;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.Date;
import java.util.List;

public interface InterfaceInvokeLogMapper extends Mapper<InterfaceInvokeLogDO> {
    void insertLog(InterfaceInvokeLogDO logDO);

    List<InterfaceInvokeLogDO> selectAllInvokeLogs(@Param("nodeName") String nodeName, @Param("keyword") String keyword);

    /**
     * 查询指定时间之后的所有日志（用于业务层统计）
     * @param startTime 开始时间
     * @return 日志列表
     */
    List<InterfaceInvokeLogDO> selectLogsByTimeRange(@Param("startTime") Date startTime);
}