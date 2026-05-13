package com.helianhealth.agent.console.controller;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.console.controller.reponse.InvokeLogRsp;
import com.helianhealth.agent.console.controller.reponse.InvokeStatsRsp;
import com.helianhealth.agent.repository.model.domain.InvokeLog;
import com.helianhealth.agent.repository.model.domain.InvokeStats;
import com.helianhealth.agent.repository.service.InterfaceInvokeLogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/console/api-log")
@Slf4j
@AllArgsConstructor
public class ApiLogController {

    private final InterfaceInvokeLogService interfaceInvokeLogService;

    @GetMapping("/all")
    public ResultData<PageList<InvokeLogRsp>> findAllInvokeLogs(
            @RequestParam(value = "nodeName", required = false) String nodeName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        try {
            PageList<InvokeLog> pageList = interfaceInvokeLogService.findAllInvokeLogs(nodeName, keyword, pageNum, pageSize);
            return ResultData.ok(toInvokeLogPage(pageList));
        } catch (Exception e) {
            log.error("获取节点参数配置列表失败", e);
            return ResultData.error("获取节点参数配置列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/stats/today")
    public ResultData<List<InvokeStatsRsp>> getTodayStats() {
        try {
            List<InvokeStatsRsp> stats = interfaceInvokeLogService.getTodayStats().stream()
                    .map(this::toInvokeStatsRsp)
                    .collect(Collectors.toList());
            return ResultData.ok(stats);
        } catch (Exception e) {
            return ResultData.error("获取今日统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/stats/last7days")
    public ResultData<List<InvokeStatsRsp>> getLast7DaysStats() {
        try {
            List<InvokeStatsRsp> stats = interfaceInvokeLogService.getLast7DaysStats().stream()
                    .map(this::toInvokeStatsRsp)
                    .collect(Collectors.toList());
            return ResultData.ok(stats);
        } catch (Exception e) {
            log.error("获取最近7天统计失败", e);
            return ResultData.error("获取最近7天统计失败: " + e.getMessage());
        }
    }

    private PageList<InvokeLogRsp> toInvokeLogPage(PageList<InvokeLog> pageList) {
        PageList<InvokeLogRsp> result = new PageList<>();
        result.setTotal(pageList.getTotal());
        result.setPageNum(pageList.getPageNum());
        result.setPageSize(pageList.getPageSize());
        result.setRows(pageList.getRows().stream()
                .map(this::toInvokeLogRsp)
                .collect(Collectors.toList()));
        return result;
    }

    private InvokeLogRsp toInvokeLogRsp(InvokeLog logModel) {
        InvokeLogRsp rsp = new InvokeLogRsp();
        rsp.setLogId(logModel.getLogId());
        rsp.setNodeName(logModel.getNodeName());
        rsp.setKeyword(logModel.getKeyword());
        rsp.setBusinessData(logModel.getBusinessData());
        rsp.setParamBeforeInvoke(logModel.getParamBeforeInvoke());
        rsp.setRemoteInvokeResponse(logModel.getRemoteInvokeResponse());
        rsp.setRemoteInvokeResponseParsed(logModel.getRemoteInvokeResponseParsed());
        rsp.setParamAfterInvoke(logModel.getParamAfterInvoke());
        rsp.setInvokeTime(logModel.getInvokeTime());
        rsp.setErrorMessage(logModel.getErrorMessage());
        rsp.setCreateTime(logModel.getCreateTime());
        return rsp;
    }

    private InvokeStatsRsp toInvokeStatsRsp(InvokeStats stats) {
        InvokeStatsRsp rsp = new InvokeStatsRsp();
        rsp.setNodeId(stats.getNodeId());
        rsp.setNodeName(stats.getNodeName());
        rsp.setCount(stats.getCount());
        rsp.setAverageTime(stats.getAverageTime());
        rsp.setMinTime(stats.getMinTime());
        rsp.setMaxTime(stats.getMaxTime());
        return rsp;
    }
}
