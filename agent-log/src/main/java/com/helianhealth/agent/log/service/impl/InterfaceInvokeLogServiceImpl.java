package com.helianhealth.agent.log.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.log.mapper.InterfaceInvokeLogMapper;
import com.helianhealth.agent.log.model.InterfaceInvokeLogDO;
import com.helianhealth.agent.log.model.InvokeStatsVO;
import com.helianhealth.agent.log.service.InterfaceInvokeLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterfaceInvokeLogServiceImpl implements InterfaceInvokeLogService {

    private final InterfaceInvokeLogMapper interfaceInvokeLogMapper;

    @Override
    public void saveInvokeLog(String nodeName, String keyword, String businessData, String paramBeforeInvoke, String remoteInvokeResponse, String paramAfterInvoke, String invokeTime) {
        try {
            InterfaceInvokeLogDO logDO = InterfaceInvokeLogDO.builder()
                    .nodeName(nodeName)
                    .keyword(keyword)
                    .businessData(businessData)
                    .paramBeforeInvoke(paramBeforeInvoke)
                    .remoteInvokeResponse(remoteInvokeResponse)
                    .paramAfterInvoke(paramAfterInvoke)
                    .invokeTime(invokeTime)
                    .createTime(new Date())
                    .build();

            interfaceInvokeLogMapper.insertLog(logDO);
        } catch (Exception e) {
            log.error("保存接口调用日志失败", e);
        }
    }

    @Override
    public void saveInvokeLog(String nodeName, String keyword, String paramBeforeInvoke, String remoteInvokeResponse, String invokeTime) {
        this.saveInvokeLog(nodeName, keyword, null, paramBeforeInvoke, remoteInvokeResponse, null, invokeTime);
    }

    @Override
    public PageList<InterfaceInvokeLogDO> findAllInvokeLogs(String nodeName, String keyword, int pageNum, int pageSize) {
        try {
            PageHelper.startPage(pageNum, pageSize);
            // 将查询结果转换为 PageInfo 对象
            List<InterfaceInvokeLogDO> invokeLogs = interfaceInvokeLogMapper.selectAllInvokeLogs(nodeName, keyword);
            PageInfo<InterfaceInvokeLogDO> pageInfo = new PageInfo<>(invokeLogs);

            // 构建 PageList 对象
            PageList<InterfaceInvokeLogDO> pageList = new PageList<>();
            pageList.setRows(invokeLogs);
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
            // 获取今日0点的时间
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date startTime = calendar.getTime();

            // 查询原始日志
            List<InterfaceInvokeLogDO> logs = interfaceInvokeLogMapper.selectLogsByTimeRange(startTime);

            // 在业务层进行统计
            return calculateStats(logs);
        } catch (Exception e) {
            log.error("获取今日统计失败", e);
            throw new RuntimeException("获取今日统计失败: " + e.getMessage());
        }
    }

    @Override
    public List<InvokeStatsVO> getLast7DaysStats() {
        try {
            // 获取最近7天0点的时间
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, -7);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date startTime = calendar.getTime();

            // 查询原始日志
            List<InterfaceInvokeLogDO> logs = interfaceInvokeLogMapper.selectLogsByTimeRange(startTime);

            // 在业务层进行统计
            return calculateStats(logs);
        } catch (Exception e) {
            log.error("获取最近7天统计失败", e);
            throw new RuntimeException("获取最近7天统计失败: " + e.getMessage());
        }
    }

    /**
     * 业务层统计逻辑：按接口分组聚合
     */
    private List<InvokeStatsVO> calculateStats(List<InterfaceInvokeLogDO> logs) {
        if (logs == null || logs.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        // 按 nodeName 分组
        java.util.Map<String, java.util.List<InterfaceInvokeLogDO>> groupedLogs = logs.stream()
                .collect(java.util.stream.Collectors.groupingBy(InterfaceInvokeLogDO::getNodeName));

        // 对每个组进行统计
        return groupedLogs.entrySet().stream()
                .map(entry -> {
                    String nodeName = entry.getKey();
                    List<InterfaceInvokeLogDO> nodeLogs = entry.getValue();

                    // 解析耗时字符串并计算统计值
                    List<Integer> times = nodeLogs.stream()
                            .map(log -> parseInvokeTime(log.getInvokeTime()))
                            .collect(java.util.stream.Collectors.toList());

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
                .sorted(java.util.Comparator.comparing(InvokeStatsVO::getCount).reversed())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 解析耗时字符串，将 "16 ms" 转换为 16
     */
    private Integer parseInvokeTime(String invokeTimeStr) {
        if (invokeTimeStr == null || invokeTimeStr.trim().isEmpty()) {
            return 0;
        }
        try {
            // 移除 "ms" 后缀和空格
            String numStr = invokeTimeStr.replaceAll("[^0-9.]", "");
            return Math.round(Float.parseFloat(numStr));
        } catch (NumberFormatException e) {
            log.warn("解析耗时失败: {}", invokeTimeStr);
            return 0;
        }
    }
}
