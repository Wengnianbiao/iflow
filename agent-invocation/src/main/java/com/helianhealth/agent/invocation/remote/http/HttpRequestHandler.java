package com.helianhealth.agent.invocation.remote.http;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.invocation.remote.ParamResolver;
import com.helianhealth.agent.invocation.remote.manager.HttpApiClientManager;
import com.helianhealth.agent.common.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.ParseException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 参数解析器
 * 负责将 ParamTreeNode 结构解析为 HTTP 调用所需的参数格式
 */
@Slf4j
@Component
public class HttpRequestHandler implements ParamResolver {

    private final HttpApiClientManager httpApiClientManager;

    public HttpRequestHandler() {
        this.httpApiClientManager = HttpApiClientManager.getInstance();
    }

    /**
     * http参数解析
     */
    public String resolveParamNodes(WorkflowNode flowNode, List<ParamTreeNode> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        // 兼容调用方的请求体是纯数组的场景,e.g. [1,2,3] or [{"name":"John"},{"name":"Mike"}]
        if (params.size() == 1 && params.get(0).getParamType() == ParamType.PURE_ARRAY) {
            return JsonUtils.toJsonString(processArrayNodeType(params.get(0)));
        }
        Map<String, Object> queryParams = new HashMap<>();

        params.forEach(param -> queryParams.put(param.getParamKey(), extractValueFromNode(param)));

        return JsonUtils.toJsonString(queryParams);
    }

    private Object extractValueFromNode(ParamTreeNode node) {
        if (node == null) {
            return null;
        }

        switch (node.getParamType()) {
            case STRING:
            case INTEGER:
            case LONG:
            case BOOLEAN:
            case DOUBLE:
                return node.getParamValue();
            case ARRAY:
                return processArrayNodeType(node);

            case OBJECT:
                if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                    Map<String, Object> objectMap = new HashMap<>();
                    for (ParamTreeNode child : node.getChildren()) {
                        if (child.getParamKey() != null) {
                            objectMap.put(child.getParamKey(), extractValueFromNode(child));
                        }
                    }
                    return objectMap;
                } else {
                    return node.getParamValue() != null ?
                            JsonUtils.toJsonString(node.getParamValue()) : null;
                }
            default:
                return node.getParamValue() != null ? node.getParamValue().toString() : null;
        }
    }

    private Object processArrayNodeType(ParamTreeNode node) {
        JSONArray jsonArray = new JSONArray();

        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            // 遍历所有虚拟节点
            for (ParamTreeNode virtualNode : node.getChildren()) {
                // 虚拟节点的子节点才是真正的元素
                if (virtualNode.getChildren() != null && !virtualNode.getChildren().isEmpty()) {
                    List<ParamTreeNode> actualNodes = virtualNode.getChildren();

                    if (actualNodes.size() == 1) {
                        // 只有一个子节点说明是单元素节点,默认只保留元素值
                        ParamTreeNode actualNode = actualNodes.get(0);
                        Object value = extractValueFromNode(actualNode);
                        // 根据配置项中是否保留单元素节点的key
                        Boolean keepSingleElementKey = virtualNode.getKeepSingleElementKey();
                        if (keepSingleElementKey != null && keepSingleElementKey) {
                            JSONObject obj = new JSONObject();
                            obj.put(actualNode.getParamKey(), value);
                            jsonArray.add(obj);
                        } else {
                            jsonArray.add(value);
                        }
                    } else {
                        // 多个子节点，封装成对象后加入数组
                        JSONObject obj = new JSONObject();
                        for (ParamTreeNode actualNode : actualNodes) {
                            if (actualNode.getParamKey() != null) {
                                Object value = extractValueFromNode(actualNode);
                                obj.put(actualNode.getParamKey(), value);
                            }
                        }
                        jsonArray.add(obj);
                    }
                }
            }
        }

        return jsonArray;
    }

    /**
     * 执行HTTP请求
     * 根据Content-Type自动选择JSON或Form格式
     */
    public String executeHttpRequest(String url, String method, Map<String, String> headers, String requestBody) throws IOException, ParseException {
        if ("GET".equalsIgnoreCase(method)) {
            return httpApiClientManager.executeGet(url, headers);
        }
        
        // 检查Content-Type，判断使用JSON还是Form格式
        String contentType = headers != null ? headers.get("Content-Type") : null;
        if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
            // Form格式：将JSON转为Map<String, String>
            Map<String, String> formParams = convertJsonToFormParams(requestBody);
            return httpApiClientManager.executePostForm(url, headers, formParams);
        }
        
        // 默认JSON格式
        return httpApiClientManager.executePost(url, headers, requestBody);
    }
    
    /**
     * 将JSON字符串转换为Form参数Map
     * 只支持一级扁平结构：{"key1":"value1", "key2":"value2"}
     */
    private Map<String, String> convertJsonToFormParams(String jsonBody) {
        Map<String, String> formParams = new HashMap<>();
        if (jsonBody == null || jsonBody.trim().isEmpty()) {
            return formParams;
        }
        
        try {
            Map<String, Object> jsonMap = JsonUtils.toMap(jsonBody);
            if (jsonMap != null) {
                jsonMap.forEach((key, value) -> {
                    if (value != null) {
                        formParams.put(key, value.toString());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Failed to convert JSON to form params: {}", jsonBody, e);
        }
        
        return formParams;
    }
}

