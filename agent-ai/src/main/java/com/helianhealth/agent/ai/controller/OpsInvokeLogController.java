package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.ai.model.InvokeLogMcpView;
import com.helianhealth.agent.repository.model.domain.InvokeLog;
import com.helianhealth.agent.repository.service.InterfaceInvokeLogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

/**
 * Ops 接口 - 接口调用日志查询（底层 REST 能力面）。
 */
@RestController
@RequestMapping("/v1/ai/ops/invoke-log")
@Slf4j
@AllArgsConstructor
public class OpsInvokeLogController {

    private final InterfaceInvokeLogService invokeLogService;

    @GetMapping("/list")
    public ResultData<PageList<InvokeLogMcpView>> listInvokeLogs(
            @RequestParam(value = "nodeName", required = false) String nodeName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        try {
            log.info("Ops 查询调用日志: nodeName={}, keyword={}, pageNum={}, pageSize={}",
                    nodeName, keyword, pageNum, pageSize);
            PageList<InvokeLog> logs = invokeLogService.findAllInvokeLogs(
                    nodeName, keyword, pageNum, pageSize);
            return ResultData.ok(toInvokeLogPage(logs));
        } catch (Exception e) {
            log.error("Ops 查询调用日志失败", e);
            return ResultData.error("查询调用日志失败: " + e.getMessage());
        }
    }

    @GetMapping("/latest")
    public ResultData<PageList<InvokeLogMcpView>> getLatestLogs(
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        try {
            log.info("Ops 查询最新调用日志: limit={}", limit);
            PageList<InvokeLog> logs = invokeLogService.findAllInvokeLogs(
                    null, null, 1, limit);
            return ResultData.ok(toInvokeLogPage(logs));
        } catch (Exception e) {
            log.error("Ops 查询最新调用日志失败", e);
            return ResultData.error("查询最新调用日志失败: " + e.getMessage());
        }
    }

    @GetMapping("/by-keyword/{keyword}")
    public ResultData<PageList<InvokeLogMcpView>> getLogsByKeyword(
            @PathVariable String keyword,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        try {
            log.info("Ops 根据关键字查询日志: keyword={}, limit={}", keyword, limit);
            PageList<InvokeLog> logs = invokeLogService.findAllInvokeLogs(
                    null, keyword, 1, limit);
            return ResultData.ok(toInvokeLogPage(logs));
        } catch (Exception e) {
            log.error("Ops 根据关键字查询日志失败", e);
            return ResultData.error("根据关键字查询日志失败: " + e.getMessage());
        }
    }

    @GetMapping("/by-node/{nodeName}")
    public ResultData<PageList<InvokeLogMcpView>> getLogsByNode(
            @PathVariable String nodeName,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        try {
            log.info("Ops 根据节点查询日志: nodeName={}, pageNum={}, pageSize={}",
                    nodeName, pageNum, pageSize);
            PageList<InvokeLog> logs = invokeLogService.findAllInvokeLogs(
                    nodeName, null, pageNum, pageSize);
            return ResultData.ok(toInvokeLogPage(logs));
        } catch (Exception e) {
            log.error("Ops 根据节点查询日志失败", e);
            return ResultData.error("根据节点查询日志失败: " + e.getMessage());
        }
    }

    private PageList<InvokeLogMcpView> toInvokeLogPage(PageList<InvokeLog> logs) {
        PageList<InvokeLogMcpView> page = new PageList<>();
        page.setTotal(logs.getTotal());
        page.setPageNum(logs.getPageNum());
        page.setPageSize(logs.getPageSize());
        page.setRows(logs.getRows().stream()
                .map(this::toInvokeLogView)
                .collect(Collectors.toList()));
        return page;
    }

    private InvokeLogMcpView toInvokeLogView(InvokeLog logModel) {
        InvokeLogMcpView view = new InvokeLogMcpView();
        view.setLogId(logModel.getLogId());
        view.setNodeName(logModel.getNodeName());
        view.setKeyword(logModel.getKeyword());
        view.setBusinessData(logModel.getBusinessData());
        view.setParamBeforeInvoke(logModel.getParamBeforeInvoke());
        view.setRemoteInvokeResponse(logModel.getRemoteInvokeResponse());
        view.setRemoteInvokeResponseParsed(logModel.getRemoteInvokeResponseParsed());
        view.setParamAfterInvoke(logModel.getParamAfterInvoke());
        view.setInvokeTime(logModel.getInvokeTime());
        view.setErrorMessage(logModel.getErrorMessage());
        view.setCreateTime(logModel.getCreateTime());
        return view;
    }
}
