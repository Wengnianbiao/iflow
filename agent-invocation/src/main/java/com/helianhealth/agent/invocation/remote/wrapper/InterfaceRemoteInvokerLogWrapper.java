package com.helianhealth.agent.invocation.remote.wrapper;

import com.helianhealth.agent.invocation.context.InvocationLogContextHolder;
import com.helianhealth.agent.invocation.context.LogContext;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.repository.model.dto.InvokeLogDTO;
import com.helianhealth.agent.repository.service.InterfaceInvokeLogService;
import com.helianhealth.agent.common.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class InterfaceRemoteInvokerLogWrapper implements InterfaceRemoteInvoker {

    private final InterfaceInvokeLogService interfaceInvokeLogService;

    private final InterfaceRemoteInvoker interfaceRemoteInvoker;


    public InterfaceRemoteInvokerLogWrapper(InterfaceRemoteInvoker interfaceRemoteInvoker,
                                           InterfaceInvokeLogService interfaceInvokeLogService) {
        this.interfaceRemoteInvoker = interfaceRemoteInvoker;
        this.interfaceInvokeLogService = interfaceInvokeLogService;
    }


    @Override
    public Map<String, Object> remoteInvoke(WorkflowNode flowNode, Invocation invocation) {
        // 获取方法参数
        long startTime = System.currentTimeMillis();

        LogContext context = new LogContext();
        InvocationLogContextHolder.setContext(context);
        context.setNodeName(flowNode.getNodeName());
        Map<String, Object> businessData = invocation.getBusinessData();
        // 提取关键字
        if (businessData != null && flowNode.getKeywordField() != null) {
            Object keywordObj = getNestedValue(businessData, flowNode.getKeywordField());
            if (keywordObj != null) {
                context.setKeyword(String.valueOf(keywordObj));
            }
        }
        context.setBusinessData(JsonUtils.toJsonString(businessData));

        try {
            log.info("开始执行工作流节点: [{}]", flowNode.getNodeName());

            // 执行原方法
            Map<String, Object> stringObjectMap = interfaceRemoteInvoker.remoteInvoke(flowNode, invocation);

            // 记录调用结果
            context.setParamAfterInvoke(JsonUtils.toJsonString(stringObjectMap));

            return stringObjectMap;
        } catch (Exception e) {
            log.error("工作流节点执行异常", e);
            throw e;
        } finally {
            try {
                saveAllCollectedLogs(context);
                log.info("工作流节点: [{}] 执行结束, 耗时: [{}]ms", flowNode.getNodeName(), System.currentTimeMillis() - startTime);
            } catch (Exception e) {
                log.error("日志保存失败", e);
            } finally {
                // 清除ThreadLocal数据，防止内存泄漏
                InvocationLogContextHolder.clearContext();
            }
        }
    }


    /**
     * 从嵌套的Map结构中递归获取值
     * 支持通过点号(.)分隔的嵌套路径，例如: "patient.info.code"
     *
     * @param data 数据Map
     * @param path 嵌套路径
     * @return 提取到的值，如果未找到则返回null
     */
    private Object getNestedValue(Map<String, Object> data, String path) {
        if (data == null || path == null || path.isEmpty()) {
            return null;
        }

        String[] keys = path.split("\\.");
        Object current = data;

        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
                if (current == null) {
                    return null;
                }
            } else {
                return null;
            }
        }

        return current;
    }


    /**
     * 统一保存所有收集到的日志数据
     */
    private void saveAllCollectedLogs(LogContext context) {
        try {
            InvokeLogDTO dto = InvokeLogDTO.builder()
                    .nodeName(context.getNodeName())
                    .keyword(context.getKeyword())
                    .businessData(context.getBusinessData())
                    .paramBeforeInvoke(context.getParamBeforeInvoke())
                    .remoteInvokeResponse(context.getRemoteInvokeResponse())
                    .paramAfterInvoke(context.getParamAfterInvoke())
                    .invokeTime(context.getInvokeTime())
                    .build();
            interfaceInvokeLogService.saveInvokeLog(dto);
        } catch (Exception e) {
            log.error("保存接口调用日志失败", e);
        }
    }
}
