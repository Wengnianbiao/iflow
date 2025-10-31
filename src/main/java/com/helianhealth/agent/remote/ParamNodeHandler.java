package com.helianhealth.agent.remote;

import com.helianhealth.agent.enums.ParamType;
import com.helianhealth.agent.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import com.helianhealth.agent.remote.resolver.ValueResolverSelector;
import com.helianhealth.agent.utils.JsonUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.URL;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 参数节点处理器接口
 */
@Slf4j
public abstract class ParamNodeHandler implements ParamNode{


    @Setter
    private ValueResolverSelector valueResolverSelector;

    @Override
    public List<ParamTreeNode> buildParamTree(URL url,List<NodeParamConfigDO> configs,
                                              Integer parentId,
                                              Map<String, Object> businessData,
                                              Map<String, Object> sourceBusinessData) {
        return doBuildParamTree(configs, parentId, businessData, sourceBusinessData);
    }


    public List<ParamTreeNode> buildParamTree(List<NodeParamConfigDO> configs,
                                              Integer parentId,
                                              Map<String, Object> businessData,
                                              Map<String, Object> sourceBusinessData) {
        return doBuildParamTree(configs, parentId, businessData, sourceBusinessData);
    }

    private List<ParamTreeNode> doBuildParamTree(List<NodeParamConfigDO> configs,
                                                 Integer parentId,
                                                 Map<String, Object> businessData,
                                                 Map<String, Object> sourceBusinessData) {
        List<ParamTreeNode> children = new ArrayList<>();

        try {
            // 1、从root节点开始构建参数树,筛选当前父节点的子节点
            List<NodeParamConfigDO> currentNodes = configs.stream()
                    .filter(node -> Objects.equals(node.getParentId(), parentId))
                    .sorted(Comparator.comparingInt(NodeParamConfigDO::getSort))
                    .collect(Collectors.toList());

            // 2、组装每个节点的参数值
            for (NodeParamConfigDO config : currentNodes) {
                children.add(processParamNode(config, configs, businessData, sourceBusinessData));
            }
        } catch (Exception e) {
            log.error("convert param error:", e);
        }

        return children;
    }

    private ParamTreeNode processParamNode(NodeParamConfigDO config,
                                           List<NodeParamConfigDO> allNodes,
                                           Map<String, Object> businessData,
                                           Map<String, Object> sourceBusinessData) {
        ParamTreeNode node = ParamTreeNode.builder()
                .paramKey(config.getTargetParamKey())
                .paramType(config.getTargetParamType())
                .build();

        // 根据节点类型处理
        switch (config.getTargetParamType()) {
            case OBJECT:
                // 对象类型处理
                processObjectNodeType(config, allNodes, businessData, sourceBusinessData, node);
                break;
            case ARRAY:
            case PURE_ARRAY:
                // 数组类型处理
                processArrayNodeType(config, allNodes, businessData, sourceBusinessData, node);
                break;
            default:
                // 基本类型：从业务数据中取值（根据mappingRule映射）
                node.setParamValue(resolveValue(config, businessData, sourceBusinessData));
        }

        return node;
    }

    public Object resolveValue(NodeParamConfigDO config, Map<String, Object> businessData, Map<String, Object> sourceBusinessData) {
        return valueResolverSelector.resolveValue(config, businessData, sourceBusinessData);
    }

    protected Map<String, Object> processSourceParamWhenTargetIsObject(NodeParamConfigDO config, Map<String, Object> businessData) {
        ParamType sourceParamType = config.getSourceParamType();
        Object sourceValue = businessData.get(config.getSourceParamKey());
        switch(sourceParamType) {
            case NONE:
                return businessData;
            // 若为对象直接返回
            case OBJECT:
                return JsonUtils.toMap(sourceValue);
            // 若为数组则需要转换成当前数组的首个索引对应的对象的key-value格式
            // 默认获取索引为0的元素
            case ARRAY:
                if (sourceValue == null) {
                    return null;
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

                // 安全转换并获取第一个元素
                return JsonUtils.toMap(list.get(0));
            default:
                return new HashMap<>();
        }
    }

    protected void processArrayNodeTypeWhenSourceTypeIsArray(NodeParamConfigDO config,
                                                           List<NodeParamConfigDO> allNodes,
                                                           Object sourceValue,
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
                    rootBusinessData);
            allArrayChildren.addAll(nestedArrayChildren);
        }

        node.setChildren(allArrayChildren);
    }


    public abstract void processObjectNodeType(NodeParamConfigDO config,
                                               List<NodeParamConfigDO> allNodes,
                                               Map<String, Object> businessData,
                                               Map<String, Object> rootBusinessData,
                                               ParamTreeNode node);

    public abstract void processArrayNodeType(NodeParamConfigDO config,
                                              List<NodeParamConfigDO> allNodes,
                                              Map<String, Object> businessData,
                                              Map<String, Object> rootBusinessData,
                                              ParamTreeNode node);
}
