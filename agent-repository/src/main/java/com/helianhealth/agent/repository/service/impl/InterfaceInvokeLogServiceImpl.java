package com.helianhealth.agent.repository.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.repository.mapper.agent.InterfaceInvokeLogMapper;
import com.helianhealth.agent.repository.model.po.InterfaceInvokeLogDO;
import com.helianhealth.agent.repository.model.dto.InvokeLogDTO;
import com.helianhealth.agent.repository.model.dto.InvokeLogVO;
import com.helianhealth.agent.repository.model.dto.InvokeStatsVO;
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

    @Override
    public void saveInvokeLog(InvokeLogDTO dto) {
        try {
            InterfaceInvokeLogDO logDO = InterfaceInvokeLogDO.builder()
                    .nodeName(dto.getNodeName())
                    .keyword(dto.getKeyword())
                    .businessData(dto.getBusinessData())
                    .paramBeforeInvoke(dto.getParamBeforeInvoke())
                    .remoteInvokeResponse(dto.getRemoteInvokeResponse())
                    .paramAfterInvoke(dto.getParamAfterInvoke())
                    .invokeTime(dto.getInvokeTime())
                    .createTime(new Date())
                    .build();

            interfaceInvokeLogMapper.insertLog(logDO);
        } catch (Exception e) {
            log.error("保存接口调用日志失败", e);
        }
    }

    @Override
    public PageList<InvokeLogVO> findAllInvokeLogs(String nodeName, String keyword, int pageNum, int pageSize) {
        try {
            PageHelper.startPage(pageNum, pageSize);
            List<InterfaceInvokeLogDO> invokeLogs = interfaceInvokeLogMapper.selectAllInvokeLogs(nodeName, keyword);
            PageInfo<InterfaceInvokeLogDO> pageInfo = new PageInfo<>(invokeLogs);

            List<InvokeLogVO> voList = invokeLogs.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            PageList<InvokeLogVO> pageList = new PageList<>();
            pageList.setRows(voList);
            pageList.setTotal(pageInfo.getTotal());
            pageList.setPageNum(pageInfo.getPageNum());
            pageList.setPageSize(pageInfo.getPageSize());
            return pageList;
        } catch (Exception e) {
            log.error("获取接口调用日志列表失败", e);
            throw new RuntimeException("获取接口调用日志列表失败: " + e.getMessage());
        }
    }

    @Override
    public List<InvokeStatsVO> getTodayStats() {
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date startTime = calendar.getTime();

            List<InterfaceInvokeLogDO> logs = interfaceInvokeLogMapper.selectLogsByTimeRange(startTime);
            return calculateStats(logs);
        } catch (Exception e) {
            log.error("获取今日统计失败", e);
            throw new RuntimeException("获取今日统计失败: " + e.getMessage());
        }
    }

    @Override
    public List<InvokeStatsVO> getLast7DaysStats() {
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, -7);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date startTime = calendar.getTime();

            List<InterfaceInvokeLogDO> logs = interfaceInvokeLogMapper.selectLogsByTimeRange(startTime);
            return calculateStats(logs);
        } catch (Exception e) {
            log.error("获取最近7天统计失败", e);
            throw new RuntimeException("获取最近7天统计失败: " + e.getMessage());
        }
    }

    private InvokeLogVO convertToVO(InterfaceInvokeLogDO logDO) {
        return InvokeLogVO.builder()
                .logId(logDO.getLogId())
                .nodeName(logDO.getNodeName())
                .keyword(logDO.getKeyword())
                .businessData(logDO.getBusinessData())
                .paramBeforeInvoke(logDO.getParamBeforeInvoke())
                .remoteInvokeResponse(logDO.getRemoteInvokeResponse())
                .paramAfterInvoke(logDO.getParamAfterInvoke())
                .invokeTime(logDO.getInvokeTime())
                .createTime(logDO.getCreateTime())
                .build();
    }

    private List<InvokeStatsVO> calculateStats(List<InterfaceInvokeLogDO> logs) {
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, List<InterfaceInvokeLogDO>> groupedLogs = logs.stream()
                .collect(Collectors.groupingBy(InterfaceInvokeLogDO::getNodeName));

        return groupedLogs.entrySet().stream()
                .map(entry -> {
                    String nodeName = entry.getKey();
                    List<InterfaceInvokeLogDO> nodeLogs = entry.getValue();

                    List<Integer> times = nodeLogs.stream()
                            .map(l -> parseInvokeTime(l.getInvokeTime()))
                            .collect(Collectors.toList());

                    int count = times.size();
                    double averageTime = times.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                    int minTime = times.stream().mapToInt(Integer::intValue).min().orElse(0);
                    int maxTime = times.stream().mapToInt(Integer::intValue).max().orElse(0);

                    return InvokeStatsVO.builder()
                            .nodeName(nodeName)
                            .count(count)
                            .averageTime(averageTime)
                            .minTime(minTime)
                            .maxTime(maxTime)
                            .build();
                })
                .sorted(Comparator.comparing(InvokeStatsVO::getCount).reversed())
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
        try {
            int deletedCount = interfaceInvokeLogMapper.deleteLogsBefore(beforeTime);
            log.info("删除{}之前的日志，共{}条", beforeTime, deletedCount);
            return deletedCount;
        } catch (Exception e) {
            log.error("删除日志失败", e);
            throw new RuntimeException("删除日志失败: " + e.getMessage());
        }
    }
}
