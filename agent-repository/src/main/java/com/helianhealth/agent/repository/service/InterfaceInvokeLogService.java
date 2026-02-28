package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.repository.model.dto.InvokeLogDTO;
import com.helianhealth.agent.repository.model.dto.InvokeLogVO;
import com.helianhealth.agent.repository.model.dto.InvokeStatsVO;

import java.util.Date;
import java.util.List;

public interface InterfaceInvokeLogService {

    /**
     * 保存接口调用日志
     */
    void saveInvokeLog(InvokeLogDTO dto);

    /**
     * 分页查询调用日志
     */
    PageList<InvokeLogVO> findAllInvokeLogs(String nodeName, String keyword, int pageNum, int pageSize);

    /**
     * 统计今日各接口调用情况
     */
    List<InvokeStatsVO> getTodayStats();

    /**
     * 统计最近7天各接口调用情况
     */
    List<InvokeStatsVO> getLast7DaysStats();

    /**
     * 删除指定时间之前的日志
     * @param beforeTime 截止时间，删除该时间之前的所有日志
     * @return 删除的记录数
     */
    int deleteLogsBefore(Date beforeTime);
}
