package com.helianhealth.agent.extension;


import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.invocation.context.LogCollector;
import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.utils.HttpUtils;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.ParseException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * SPI接口拓展示例
 */
@Slf4j
public class CreateArchiveInvoker implements InterfaceRemoteInvoker {

    @Override
    public Map<String, Object> remoteInvoke(WorkflowNode interfaceWorkflowNode, Invocation invocation) {
        // 上游业务入参
        Map<String, Object> businessData = invocation.getBusinessData();
        long startTime = System.currentTimeMillis();

        try {
            // 将上游入参转换为下游接口需要的格式
            Map<String, Object> request = convertToDownstreamParam(businessData);
            String requestJson = JsonUtils.toJsonString(request);
            // 收集调用前日志
            LogCollector.collectParamBeforeInvoke(JsonUtils.toJsonString(requestJson));
            // 获取节点url
            Map<String, Object> metaInfo = JsonUtils.parseMetaInfo(interfaceWorkflowNode.getMetaInfo());
            String url = (String) metaInfo.get("url");

            // 发起 HTTP POST 请求
            String responseJson = HttpUtils.post(url, requestJson);
            // 收集调用后日志
            LogCollector.collectRemoteInvokeResponse(responseJson);

            Map<String, Object> response = JsonUtils.fromJsonStringToObjectMap(responseJson);
            // 构造返回给上游的响应
            Map<String, Object> result = new HashMap<>();
            String code = (String) response.get("code");
            result.put("code", code);
            String message = (String) response.get("message");
            result.put("message", message);
            return result;

        } catch (IOException | ParseException e) {

            log.error("调用远程接口失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("code", "500");
            result.put("message", "调用失败：" + e.getMessage());
            return result;
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            LogCollector.collectInvokeTime(durationMs);
        }
    }

    /**
     * 将上游入参转换为下游接口需要的格式
     */
    private Map<String, Object> convertToDownstreamParam(Map<String, Object> upstreamData) {
        Map<String, Object> downstreamParam = new HashMap<>();

        // 基本字段映射
        downstreamParam.put("birthday", upstreamData.get("birthday"));
        downstreamParam.put("patientName", upstreamData.get("patientName"));
        downstreamParam.put("ethnic", upstreamData.get("nation")); // nation -> ethnic
        downstreamParam.put("idType", upstreamData.get("cardType")); // cardType -> idType
        downstreamParam.put("gender", upstreamData.get("sex")); // sex -> gender
        downstreamParam.put("nation", "CHN"); // 固定值
        downstreamParam.put("examoperator", ""); // 空值或根据业务设置
        downstreamParam.put("remark", ""); // 空值
        downstreamParam.put("idNo", upstreamData.get("identitycard")); // identitycard -> idNo
        downstreamParam.put("orgCode", ""); // 空值或从 orgName 提取
        downstreamParam.put("tel", upstreamData.get("phone")); // phone -> tel
        downstreamParam.put("email", ""); // 空值

        return downstreamParam;
    }
}
