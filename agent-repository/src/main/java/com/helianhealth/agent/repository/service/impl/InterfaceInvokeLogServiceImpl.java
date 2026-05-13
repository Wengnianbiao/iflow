package com.helianhealth.agent.repository.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.repository.mapper.agent.InterfaceInvokeLogMapper;
import com.helianhealth.agent.repository.model.assembler.InvokeLogAssembler;
import com.helianhealth.agent.repository.model.domain.InvokeLog;
import com.helianhealth.agent.repository.model.domain.InvokeStats;
import com.helianhealth.agent.repository.model.po.InterfaceInvokeLogPO;
import com.helianhealth.agent.repository.service.InterfaceInvokeLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InterfaceInvokeLogServiceImpl implements InterfaceInvokeLogService {

    private final InterfaceInvokeLogMapper interfaceInvokeLogMapper;

    public InterfaceInvokeLogServiceImpl (InterfaceInvokeLogMapper interfaceInvokeLogMapper) {
        this.interfaceInvokeLogMapper = interfaceInvokeLogMapper;
    }

    private static final int MAX_LOG_LENGTH = 20000;

    @Override
    public void saveInvokeLog(InvokeLog logModel) {
        try {
            InterfaceInvokeLogPO logPO = InvokeLogAssembler.toPO(logModel);
            
            // 安全截断保护，防止大对象撑爆数据库
            logPO.setRemoteInvokeResponse(truncateString(logPO.getRemoteInvokeResponse(), "remoteInvokeResponse"));
            logPO.setRemoteInvokeResponseParsed(truncateString(logPO.getRemoteInvokeResponseParsed(), "remoteInvokeResponseParsed"));
            logPO.setBusinessData(truncateString(logPO.getBusinessData(), "businessData"));
            logPO.setParamBeforeInvoke(truncateString(logPO.getParamBeforeInvoke(), "paramBeforeInvoke"));
            logPO.setParamAfterInvoke(truncateString(logPO.getParamAfterInvoke(), "paramAfterInvoke"));

            logPO.setCreateTime(new Date());
            interfaceInvokeLogMapper.insertLog(logPO);
        } catch (Exception e) {
            log.error("保存接口调用日志失败", e);
        }
    }

    private String truncateString(String value, String fieldName) {
        if (value != null && value.length() > MAX_LOG_LENGTH) {
            log.warn("检测到字段 {} 内容过长 ({})，进行安全截断保护", fieldName, value.length());
            return value.substring(0, MAX_LOG_LENGTH) + "...[内容过长已截断]";
        }
        return value;
    }

    @Override
    public PageList<InvokeLog> findAllInvokeLogs(String nodeName, String keyword, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<InterfaceInvokeLogPO> invokeLogs = interfaceInvokeLogMapper.selectAllInvokeLogs(nodeName, keyword);
        PageInfo<InterfaceInvokeLogPO> pageInfo = new PageInfo<>(invokeLogs);

        List<InvokeLog> logList = invokeLogs.stream()
                .map(InvokeLogAssembler::toDomain)
                .collect(Collectors.toList());

        PageList<InvokeLog> pageList = new PageList<>();
        pageList.setRows(logList);
        pageList.setTotal(pageInfo.getTotal());
        pageList.setPageNum(pageInfo.getPageNum());
        pageList.setPageSize(pageInfo.getPageSize());
        return pageList;
    }

    @Override
    public List<InvokeStats> getTodayStats() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date startTime = calendar.getTime();

        List<InterfaceInvokeLogPO> logs = interfaceInvokeLogMapper.selectLogsByTimeRange(startTime);
        return calculateStats(logs);
    }

    @Override
    public List<InvokeStats> getLast7DaysStats() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -7);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date startTime = calendar.getTime();

        List<InterfaceInvokeLogPO> logs = interfaceInvokeLogMapper.selectLogsByTimeRange(startTime);
        return calculateStats(logs);
    }

    private List<InvokeStats> calculateStats(List<InterfaceInvokeLogPO> logs) {
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, List<InterfaceInvokeLogPO>> groupedLogs = logs.stream()
                .collect(Collectors.groupingBy(InterfaceInvokeLogPO::getNodeName));

        return groupedLogs.entrySet().stream()
                .map(entry -> {
                    String nodeName = entry.getKey();
                    List<InterfaceInvokeLogPO> nodeLogs = entry.getValue();

                    List<Integer> times = nodeLogs.stream()
                            .map(l -> parseInvokeTime(l.getInvokeTime()))
                            .collect(Collectors.toList());

                    int count = times.size();
                    double averageTime = times.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                    int minTime = times.stream().mapToInt(Integer::intValue).min().orElse(0);
                    int maxTime = times.stream().mapToInt(Integer::intValue).max().orElse(0);

                    return InvokeStats.builder()
                            .nodeName(nodeName)
                            .count(count)
                            .averageTime(averageTime)
                            .minTime(minTime)
                            .maxTime(maxTime)
                            .build();
                })
                .sorted(Comparator.comparing(InvokeStats::getCount).reversed())
                .collect(Collectors.toList());
    }

    private Integer parseInvokeTime(String invokeTimeStr) {
        if (invokeTimeStr == null || invokeTimeStr.trim().isEmpty()) {
            return 0;
        }
        try {
            String numStr = invokeTimeStr.replaceAll("[^0-9.]", "");
            return Math.round(Float.parseFloat(numStr));
        } catch (NumberFormatException e) {
            log.warn("解析耗时失败: {}", invokeTimeStr);
            return 0;
        }
    }

    @Override
    public int deleteLogsBefore(Date beforeTime) {
        int deletedCount = interfaceInvokeLogMapper.deleteLogsBefore(beforeTime);
        log.info("删除{}之前的日志，共{}条", beforeTime, deletedCount);
        return deletedCount;
    }
}
