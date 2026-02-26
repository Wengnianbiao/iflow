package com.helianhealth.agent.log.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.log.model.InvokeLogDTO;
import com.helianhealth.agent.log.model.InvokeLogVO;
import com.helianhealth.agent.log.model.InvokeStatsVO;

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
}
