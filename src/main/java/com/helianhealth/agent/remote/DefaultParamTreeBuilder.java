package com.helianhealth.agent.remote;

import com.helianhealth.agent.enums.ParamType;
import com.helianhealth.agent.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import com.helianhealth.agent.utils.JsonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数树构建默认实现类
 */
public class DefaultParamTreeBuilder extends AbstractParamTreeBuilder {

    @Override
    protected void processObjectNodeType(NodeParamConfigDO config,
                                         List<NodeParamConfigDO> allNodes,
                                         Map<String, Object> businessData,
                                         Map<String, Object> parentBusinessData,
                                         Map<String, Object> rootBusinessData,
                                         ParamTreeNode node) {
        // 配置项:当对象元素为单个是否保留key
        Boolean keepSingleElementKey = config.getKeepSingleElementKey();
        node.setKeepSingleElementKey(keepSingleElementKey);
        // 递归构建子节点,businessData会按照配置递归获取,parentBusinessData父参数取当前的businessData,rootBusinessData是根节点的businessData
        node.setChildren(buildParamTree(allNodes,
                config.getConfigId(),
                processSourceParamWhenTargetIsObject(config, businessData),
                businessData,
                rootBusinessData));
    }

    private Map<String, Object> processSourceParamWhenTargetIsObject(NodeParamConfigDO config, Map<String, Object> businessData) {
        ParamType sourceParamType = config.getSourceParamType();
        Object sourceValue = ProxyConvertHelper.convertSourceValueForObjectNode(config, businessData);
        switch(sourceParamType) {
            // 不需要原始参数
            case NONE:
                return businessData;
            // 若为对象直接返回
            case OBJECT:
                return JsonUtils.toMap(sourceValue);
            // 若为数组则需要转换成当前数组的首个索引对应的对象的key-value格式
            case ARRAY:
                if (sourceValue == null) {
                    throw new IllegalArgumentException("Source value for ARRAY type is null: " + config.getSourceParamKey());
                }
                if (!(sourceValue instanceof List)) {
                    throw new IllegalArgumentException("Expected List for ARRAY type, but got: " + sourceValue.getClass().getSimpleName());
                }

                List<?> list = (List<?>) sourceValue;
                if (list.isEmpty()) {
                    return new HashMap<>();
                }

                Object firstItem = list.get(0);
                if (!(firstItem instanceof Map)) {
                    throw new IllegalArgumentException("First element of array must be a Map, but got: " + firstItem.getClass().getSimpleName());
                }

                return JsonUtils.toMap(list.get(0));
            default:
                return new HashMap<>();
        }
    }


    @Override
    protected void processArrayNodeType(NodeParamConfigDO config, List<NodeParamConfigDO> allNodes, Map<String, Object> businessData, Map<String, Object> parentBusinessData, Map<String, Object> rootBusinessData, ParamTreeNode node) {
        doProcessArrayNodeType(config, allNodes, businessData, parentBusinessData, rootBusinessData, node);
    }

    private void doProcessArrayNodeType(NodeParamConfigDO config,
                                        List<NodeParamConfigDO> allNodes,
                                        Map<String, Object> businessData,
                                        Map<String, Object> parentBusinessData,
                                        Map<String, Object> rootBusinessData,
                                        ParamTreeNode node) {
        Object sourceValue = ProxyConvertHelper.convertSourceValue(config, businessData, rootBusinessData);
        if (config.getSourceParamType() == ParamType.NONE) {
            node.setChildren(buildParamTree(allNodes,
                    config.getConfigId(),
                    businessData,
                    parentBusinessData,
                    rootBusinessData));

        } else if (config.getSourceParamType() == ParamType.OBJECT && sourceValue != null) {
            // 源参数是Object，包装成大小为1的数组
            List<ParamTreeNode> arrayChildren = buildParamTree(allNodes,
                    config.getConfigId(),
                    JsonUtils.toMap(sourceValue),
                    businessData,
                    rootBusinessData);
            node.setChildren(arrayChildren);
        } else if (config.getSourceParamType() == ParamType.ARRAY && sourceValue != null) {
            // 目标参数是数组,原参数可能是数组也可能是单个对象
            processArrayNodeTypeWhenSourceTypeIsArray(config, allNodes, sourceValue, businessData, rootBusinessData, node);
        }
    }

    private void processArrayNodeTypeWhenSourceTypeIsArray(NodeParamConfigDO config,
                                                             List<NodeParamConfigDO> allNodes,
                                                             Object sourceValue,
                                                             Map<String, Object> parentBusinessData,
                                                             Map<String, Object> rootBusinessData,
                                                             ParamTreeNode node) {
        // 目标参数是数组,原参数可能是数组也可能是单个对象
        List<ParamTreeNode> allArrayChildren = new ArrayList<>();
        if (sourceValue instanceof List) {
            List<?> sourceList = (List<?>) sourceValue;
            Object o = sourceList.get(0);
            // 数组可以是基础数据类型和对象
            if (o instanceof Map) {
                for (Object item : sourceList) {
                    List<ParamTreeNode> nestedArrayChildren = buildParamTree(allNodes,
                            config.getConfigId(),
                            JsonUtils.toMap(item),
                            parentBusinessData,
                            rootBusinessData);
                    allArrayChildren.addAll(nestedArrayChildren);
                }
            } else {
                // 对于基础数据类型item是一个值,将目标映射key作为Map的key,item作为Map的value
                for (Object item : sourceList) {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put(config.getTargetParamKey(), item);
                    List<ParamTreeNode> nestedArrayChildren = buildParamTree(allNodes,
                            config.getConfigId(),
                            JsonUtils.toMap(itemMap),
                            parentBusinessData,
                            rootBusinessData);
                    allArrayChildren.addAll(nestedArrayChildren);
                }
            }
        } else {
            // 其他情况下就是数组为一维的单个基础数据类型
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put(config.getTargetParamKey(), sourceValue);
            List<ParamTreeNode> nestedArrayChildren = buildParamTree(allNodes,
                    config.getConfigId(),
                    JsonUtils.toMap(itemMap),
                    parentBusinessData,
                    rootBusinessData);
            allArrayChildren.addAll(nestedArrayChildren);
        }

        node.setChildren(allArrayChildren);
    }
}
