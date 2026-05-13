package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.repository.model.domain.InvokeLog;
import com.helianhealth.agent.repository.model.domain.InvokeStats;

import java.util.Date;
import java.util.List;

public interface InterfaceInvokeLogService {

    /**
     * 保存接口调用日志
     */
    void saveInvokeLog(InvokeLog log);

    /**
     * 分页查询调用日志
     */
    PageList<InvokeLog> findAllInvokeLogs(String nodeName, String keyword, int pageNum, int pageSize);

    /**
     * 统计今日各接口调用情况
     */
    List<InvokeStats> getTodayStats();

    /**
     * 统计最近7天各接口调用情况
     */
    List<InvokeStats> getLast7DaysStats();

    /**
     * 删除指定时间之前的日志
     * @param beforeTime 截止时间，删除该时间之前的所有日志
     * @return 删除的记录数
     */
    int deleteLogsBefore(Date beforeTime);
}
