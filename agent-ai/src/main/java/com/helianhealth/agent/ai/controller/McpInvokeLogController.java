package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import com.helianhealth.agent.repository.model.dto.InvokeLogVO;
import com.helianhealth.agent.repository.service.InterfaceInvokeLogService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP接口 - 接口调用日志查询
 * 提供给AI使用的日志查询接口，用于查看参数映射转换过程
 */
@RestController
@RequestMapping("/v1/ai/mcp/invoke-log")
@Slf4j
@AllArgsConstructor
public class McpInvokeLogController {

    private final InterfaceInvokeLogService invokeLogService;

    /**
     * 分页查询接口调用日志
     * AI使用此接口查看Mock调用的参数转换详情
     *
     * @param nodeName 节点名称（可选）
     * @param keyword 关键字（可选，如patientCode）
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 日志列表，包含前置映射结果、远程响应、后置映射结果
     */
    @GetMapping("/list")
    public ResultData<PageList<InvokeLogVO>> listInvokeLogs(
            @RequestParam(value = "nodeName", required = false) String nodeName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        try {
            log.info("MCP查询调用日志: nodeName={}, keyword={}, pageNum={}, pageSize={}", 
                    nodeName, keyword, pageNum, pageSize);
            PageList<InvokeLogVO> logs = invokeLogService.findAllInvokeLogs(
                    nodeName, keyword, pageNum, pageSize);
            return ResponseRenderUtils.render(logs);
        } catch (Exception e) {
            log.error("MCP查询调用日志失败", e);
            return ResponseRenderUtils.error("查询调用日志失败: " + e.getMessage());
        }
    }

    /**
     * 查询最新的N条日志
     * AI验证完Mock后，快速查看最新的调用记录
     *
     * @param limit 返回数量，默认5条
     * @return 最新的日志列表
     */
    @GetMapping("/latest")
    public ResultData<PageList<InvokeLogVO>> getLatestLogs(
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        try {
            log.info("MCP查询最新调用日志: limit={}", limit);
            PageList<InvokeLogVO> logs = invokeLogService.findAllInvokeLogs(
                    null, null, 1, limit);
            return ResponseRenderUtils.render(logs);
        } catch (Exception e) {
            log.error("MCP查询最新调用日志失败", e);
            return ResponseRenderUtils.error("查询最新调用日志失败: " + e.getMessage());
        }
    }

    /**
     * 根据关键字查询最近的调用日志
     * AI可以通过业务关键字（如patientCode）快速定位到相关的调用记录
     *
     * @param keyword 业务关键字
     * @param limit 返回数量，默认10条
     * @return 匹配的日志列表
     */
    @GetMapping("/by-keyword/{keyword}")
    public ResultData<PageList<InvokeLogVO>> getLogsByKeyword(
            @PathVariable String keyword,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        try {
            log.info("MCP根据关键字查询日志: keyword={}, limit={}", keyword, limit);
            PageList<InvokeLogVO> logs = invokeLogService.findAllInvokeLogs(
                    null, keyword, 1, limit);
            return ResponseRenderUtils.render(logs);
        } catch (Exception e) {
            log.error("MCP根据关键字查询日志失败", e);
            return ResponseRenderUtils.error("根据关键字查询日志失败: " + e.getMessage());
        }
    }

    /**
     * 根据节点名称查询日志
     * AI可以查看特定节点的所有调用记录
     *
     * @param nodeName 节点名称
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 该节点的日志列表
     */
    @GetMapping("/by-node/{nodeName}")
    public ResultData<PageList<InvokeLogVO>> getLogsByNode(
            @PathVariable String nodeName,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        try {
            log.info("MCP根据节点查询日志: nodeName={}, pageNum={}, pageSize={}", 
                    nodeName, pageNum, pageSize);
            PageList<InvokeLogVO> logs = invokeLogService.findAllInvokeLogs(
                    nodeName, null, pageNum, pageSize);
            return ResponseRenderUtils.render(logs);
        } catch (Exception e) {
            log.error("MCP根据节点查询日志失败", e);
            return ResponseRenderUtils.error("根据节点查询日志失败: " + e.getMessage());
        }
    }
}
