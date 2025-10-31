package com.helianhealth.agent.remote.wrapper;


import com.helianhealth.agent.log.context.LogContextHolder;
import com.helianhealth.agent.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.model.dto.LogContext;
import com.helianhealth.agent.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.service.InterfaceInvokeLogService;
import com.helianhealth.agent.utils.JsonUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class InterfaceRemoteInvokerLogWrapper implements InterfaceRemoteInvoker {

    @Setter
    private InterfaceInvokeLogService interfaceInvokeLogService;

    private final InterfaceRemoteInvoker interfaceRemoteInvoker;

    public InterfaceRemoteInvokerLogWrapper(InterfaceRemoteInvoker interfaceRemoteInvoker){
        this.interfaceRemoteInvoker = interfaceRemoteInvoker;
    }

    @Override
    public Map<String, Object> remoteInvoke(InterfaceWorkflowNode flowNode, Map<String, Object> businessData) {
        // 获取方法参数
        long startTime = System.currentTimeMillis();

        LogContext context = new LogContext();
        LogContextHolder.setContext(context);

        context.setNodeId(flowNode.getNodeId());
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
            Map<String, Object> stringObjectMap = interfaceRemoteInvoker.remoteInvoke(flowNode, businessData);

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
                LogContextHolder.clearContext();
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
            interfaceInvokeLogService.saveInvokeLog(
                    context.getNodeId(),
                    context.getKeyword(),
                    context.getBusinessData(),
                    context.getParamBeforeInvoke(),
                    context.getRemoteInvokeResponse(),
                    context.getParamAfterInvoke(),
                    context.getInvokeTime()
            );
        } catch (Exception e) {
            log.error("保存接口调用日志失败", e);
        }
    }
}
