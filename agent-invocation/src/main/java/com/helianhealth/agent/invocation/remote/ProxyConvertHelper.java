package com.helianhealth.agent.invocation.remote;

import com.helianhealth.agent.common.base.MetaInfoKeys;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.invocation.utils.ParamNodeUtils;
import com.helianhealth.agent.common.utils.JsonUtils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
public class ProxyConvertHelper {

    public static Map<String, Object> convertResponse(WorkflowNode flowNode, List<ParamTreeNode> paramTree) {
        // 获取节点的元信息
        String metaInfo = flowNode.getMetaInfo();
        if (metaInfo == null || metaInfo.isEmpty()) {
            // 如果没有元信息默认使用JSON
            return convertToJsonFormatMap(paramTree);
        }

        try {
            Map<String, Object> metaJson = JsonUtils.fromJsonStringToObjectMap(metaInfo);
            // 获取响应类型，默认为JSON
            String responseType = (String) metaJson.get(MetaInfoKeys.RESPONSE_TYPE);
            if (responseType == null || responseType.isEmpty()) {
                responseType = "json";
            }

            // 根据响应类型进行不同处理
            if ("xml".equalsIgnoreCase(responseType)) {
                return ParamNodeUtils.convertToXmlFormatMap(paramTree);
            } else {
                return convertToJsonFormatMap(paramTree);
            }
        } catch (Exception e) {
            log.error("解析节点元信息失败，nodeId: {}, metaInfo: {}", flowNode.getNodeId(), metaInfo, e);
            return ParamNodeUtils.convertToJsonFormatMap(paramTree);
        }
    }

    private static Map<String, Object> convertToJsonFormatMap(List<ParamTreeNode> paramTree) {
        return ParamNodeUtils.convertToJsonFormatMap(paramTree);
    }

    @SuppressWarnings("unchecked")
    public static Object convertSourceValue(NodeParamConfig config, Map<String, Object> businessData) {
        String sourceParamKey = config.getSourceParamKey();
        // 如果源参数key为空，则说明不需要进行转化
        if (sourceParamKey == null || sourceParamKey.isEmpty()) {
            return businessData;
        }
        String[] keys = sourceParamKey.split("\\.");
        Object current = businessData;

        for (String key : keys) {
            if (!(current instanceof Map)) {
                return null; // 非 Map 类型无法继续深入
            }

            Map<String, Object> currentMap = (Map<String, Object>) current;
            current = currentMap.get(key);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    @SuppressWarnings("unchecked")
    public static Object convertSourceValueForObjectNode(NodeParamConfig config, Map<String, Object> businessData) {
        String sourceParamKey = config.getSourceParamKey();
        // 如果源参数key为空，则说明不需要进行转化
        if (sourceParamKey == null || sourceParamKey.isEmpty()) {
            return businessData;
        }
        String[] keys = sourceParamKey.split("\\.");
        Object current = businessData;

        for (String key : keys) {
            if (!(current instanceof Map)) {
                return null; // 非 Map 类型无法继续深入
            }

            Map<String, Object> currentMap = (Map<String, Object>) current;
            current = currentMap.get(key);
            if (current == null) {
                return null;
            }
        }

        return current;
    }
}

