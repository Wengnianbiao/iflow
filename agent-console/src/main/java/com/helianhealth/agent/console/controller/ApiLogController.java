package com.helianhealth.agent.console.controller;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.repository.model.dto.InvokeLogVO;
import com.helianhealth.agent.repository.model.dto.InvokeStatsVO;
import com.helianhealth.agent.repository.service.InterfaceInvokeLogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/console/api-log")
@Slf4j
@AllArgsConstructor
public class ApiLogController {

    private final InterfaceInvokeLogService interfaceInvokeLogService;

    @GetMapping("/all")
    public ResultData<PageList<InvokeLogVO>> findAllInvokeLogs(
            @RequestParam(value = "nodeName", required = false) String nodeName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        try {
            PageList<InvokeLogVO> pageList = interfaceInvokeLogService.findAllInvokeLogs(nodeName, keyword, pageNum, pageSize);
            return ResultData.ok(pageList);
        } catch (Exception e) {
            log.error("获取节点参数配置列表失败", e);
            return ResultData.error("获取节点参数配置列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/stats/today")
    public ResultData<List<InvokeStatsVO>> getTodayStats() {
        try {
            List<InvokeStatsVO> stats = interfaceInvokeLogService.getTodayStats();
            return ResultData.ok(stats);
        } catch (Exception e) {
            return ResultData.error("获取今日统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/stats/last7days")
    public ResultData<List<InvokeStatsVO>> getLast7DaysStats() {
        try {
            List<InvokeStatsVO> stats = interfaceInvokeLogService.getLast7DaysStats();
            return ResultData.ok(stats);
        } catch (Exception e) {
            log.error("获取最近7天统计失败", e);
            return ResultData.error("获取最近7天统计失败: " + e.getMessage());
        }
    }
}
