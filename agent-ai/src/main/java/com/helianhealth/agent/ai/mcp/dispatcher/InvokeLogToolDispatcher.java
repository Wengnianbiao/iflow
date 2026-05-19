package com.helianhealth.agent.ai.mcp.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.ai.model.InvokeLogMcpView;
import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.repository.model.domain.InvokeLog;
import com.helianhealth.agent.repository.service.InterfaceInvokeLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Tool: {@code agent_invoke_log} —— 调用日志查询域级 Tool 分发器。
 */
@Slf4j
@Component
public class InvokeLogToolDispatcher extends McpDispatcherSupport implements McpToolDispatcher {

    public static final String TOOL_NAME = "agent_invoke_log";

    private final InterfaceInvokeLogService invokeLogService;

    public InvokeLogToolDispatcher(ObjectMapper objectMapper,
                                   InterfaceInvokeLogService invokeLogService) {
        super(objectMapper);
        this.invokeLogService = invokeLogService;
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public Object call(JsonNode arguments) {
        String action = requireAction(arguments);
        switch (action) {
            case "list":
                return query(
                        optString(arguments, "nodeName"),
                        optString(arguments, "keyword"),
                        optIntOr(arguments, "pageNum", 1),
                        optIntOr(arguments, "pageSize", 10));
            case "latest":
                return query(null, null, 1, optIntOr(arguments, "limit", 5));
            case "by_keyword":
                return query(null, requireString(arguments, "keyword"),
                        1, optIntOr(arguments, "limit", 10));
            case "by_node":
                return query(requireString(arguments, "nodeName"), null,
                        optIntOr(arguments, "pageNum", 1),
                        optIntOr(arguments, "pageSize", 10));
            default:
                throw new IllegalArgumentException("不支持的 action: " + action);
        }
    }

    private PageList<InvokeLogMcpView> query(String nodeName, String keyword, int pageNum, int pageSize) {
        PageList<InvokeLog> logs = invokeLogService.findAllInvokeLogs(nodeName, keyword, pageNum, pageSize);
        return toInvokeLogPage(logs);
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
