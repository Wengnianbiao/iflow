package com.helianhealth.agent.invocation.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.common.utils.JsonUtils;


import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ParamNodeUtils {

    /**
     * 将ParamNodeDTO列表转换为Map<String, Object>
     * 递归处理子节点，支持嵌套对象结构
     */
    public static Map<String, Object> convertToJsonFormatMap(List<ParamTreeNode> nodeList) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (nodeList == null || nodeList.isEmpty()) {
            return result;
        }

        for (ParamTreeNode node : nodeList) {
            String key = node.getParamKey();
            boolean isArray = node.getParamType() == ParamType.ARRAY;

            // 处理数组类型：将所有子节点放入JSONArray
            if (isArray) {
                JSONArray jsonArray = new JSONArray();
                // 遍历所有子节点，转换后添加到JSONArray
                if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                    for (ParamTreeNode child : node.getChildren()) {
                        // 转换子节点为Map或值
                        Object childValue = child.getChildren() != null && !child.getChildren().isEmpty()
                                ? convertToJsonFormatMap(child.getChildren())
                                : child.getParamValue();
                        jsonArray.add(childValue);
                    }
                }
                // 将JSONArray放入结果Map
                result.put(key, jsonArray);
            } else {
                // 非数组类型：如果有子节点则递归转换，否则直接使用节点的value
                Object value = node.getChildren() != null && !node.getChildren().isEmpty()
                        ? convertToJsonFormatMap(node.getChildren())
                        : node.getParamValue();
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * 将ParamNodeDTO转换为XML字符串
     * @param node 根节点
     * @return XML字符串
     */
    public static String paramNode2Xml(ParamTreeNode node) {
        if (node == null) {
            return "";
        }

        StringBuilder xml = new StringBuilder();
        paramNode2Xml(node, xml, 0);
        return xml.toString();
    }

    /**
     * 将ParamNodeDTO转换为XML字符串
     * @param node 根节点
     * @return XML字符串
     */
    public static String paramNode2Json(List<ParamTreeNode> node) {
        if (node == null || node.isEmpty()) {
            return "";
        }
        // 兼容调用方的请求体是纯数组的场景,e.g. [1,2,3] or [{"name":"John"},{"name":"Mike"}]
        if (node.size() == 1 && node.get(0).getParamType() == ParamType.PURE_ARRAY) {
            return JsonUtils.toJsonString(processArrayNodeType(node.get(0)));
        }
        Map<String, Object> queryParams = new HashMap<>();

        node.forEach(param -> queryParams.put(param.getParamKey(), extractValueFromNode(param)));

        return JsonUtils.toJsonString(queryParams);
    }

    private static Object extractValueFromNode(ParamTreeNode node) {
        if (node == null) {
            return null;
        }

        switch (node.getParamType()) {
            case STRING:
            case INTEGER:
            case LONG:
            case BOOLEAN:
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

    private static Object processArrayNodeType(ParamTreeNode node) {
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
     * 递归构建XML
     * @param node 当前节点
     * @param xml StringBuilder对象
     * @param indent 缩进级别
     */
    private static void paramNode2Xml(ParamTreeNode node, StringBuilder xml, int indent) {
        if (node == null) {
            return;
        }

        String tagName = node.getParamKey();
        ParamType paramType = node.getParamType();

        // 添加缩进
        addIndent(xml, indent);

        // 开始标签
        xml.append("<").append(tagName);

        // 添加属性
        List<ParamTreeNode> attributeNodes = node.getAttributeNodes();
        if (attributeNodes != null && !attributeNodes.isEmpty()) {
            for (ParamTreeNode attrNode : attributeNodes) {
                if (attrNode.getParamType() == ParamType.STRING && attrNode.getParamValue() != null) {
                    xml.append(" ").append(attrNode.getParamKey()).append("=\"")
                            .append(attrNode.getParamValue().toString()).append("\"");
                }
            }
        }

        // 判断是否为自闭合标签
        boolean isSelfClosing = isSelfClosingTag(node);

        if (isSelfClosing) {
            // 自闭合标签
            xml.append("/>\n");
        } else if (paramType == ParamType.OBJECT) {
            // 普通标签
            xml.append(">");

            List<ParamTreeNode> children = node.getChildren();
            if (children != null && !children.isEmpty()) {
                xml.append("\n");
                for (ParamTreeNode child : children) {
                    paramNode2Xml(child, xml, indent + 1);
                }
                addIndent(xml, indent);
            }

            xml.append("</").append(tagName).append(">\n");
        }  else if (paramType == ParamType.ARRAY) {
            // 数组类型：父标签在最外面，子节点使用自己的key和value
            xml.append(">");

            List<ParamTreeNode> children = node.getChildren();
            if (children != null && !children.isEmpty()) {
                xml.append("\n");
                for (ParamTreeNode child : children) {
                    paramNode2Xml(child, xml, indent + 1);
                }
                addIndent(xml, indent);
            }

            xml.append("</").append(tagName).append(">\n");
        } else {
            // 有值的标签
            if (node.getParamValue() != null) {
                xml.append(">").append(node.getParamValue().toString())
                        .append("</").append(tagName).append(">\n");
            } else {
                xml.append("/>\n");
            }
        }
    }

    /**
     * 判断是否为自闭合标签
     * @param node 节点
     * @return 是否为自闭合标签
     */
    private static boolean isSelfClosingTag(ParamTreeNode node) {
        // 如果没有子节点且没有值，则为自闭合标签
        return (node.getChildren() == null || node.getChildren().isEmpty())
                && node.getParamValue() == null
                && node.getParamType() != ParamType.STRING;
    }

    /**
     * 添加缩进
     * @param xml StringBuilder对象
     * @param indent 缩进级别
     */
    private static void addIndent(StringBuilder xml, int indent) {
        for (int i = 0; i < indent * 4; i++) {
            xml.append(" ");
        }
    }

    public static Map<String, Object> convertToXmlFormatMap(List<ParamTreeNode> nodeList) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (nodeList == null || nodeList.isEmpty()) {
            return result;
        }

        for (ParamTreeNode node : nodeList) {
            String key = node.getParamKey();
            // 处理值：如果有子节点则递归转换，否则直接使用节点的value
            Object value = node.getChildren() != null && !node.getChildren().isEmpty()
                    ? convertToXmlFormatMap(node.getChildren())
                    : node.getParamValue();

            result.put(key, value);
        }
        return result;
    }
}
