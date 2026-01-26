package com.helianhealth.agent.invocation.remote;

import com.helianhealth.agent.common.enums.MappingSource;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.invocation.remote.model.NodeParamConfig;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.invocation.remote.model.PostBuildContext;
import com.helianhealth.agent.invocation.remote.model.PreBuildContext;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.invocation.remote.resolver.ValueResolverSelector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数树构建默认实现类
 */
public class DefaultParamTreeBuilder extends AbstractParamTreeBuilder {

    /**
     * 无参构造函数，用于SPI机制实例化
     * ValueResolverSelector将通过ExtensionFactory自动注入（Setter注入）
     */
    public DefaultParamTreeBuilder() {
        super();
    }

    /**
     * 带参构造函数，支持手动注入依赖
     */
    public DefaultParamTreeBuilder(ValueResolverSelector valueResolverSelector) {
        super(valueResolverSelector);
    }

    @Override
    protected void processPreObjectNodeType(NodeParamConfig config,
                                            List<NodeParamConfig> allNodes,
                                            PreBuildContext context,
                                            ParamTreeNode node) {
        // 配置项:当对象元素为单个是否保留key
        Boolean keepSingleElementKey = config.getKeepSingleElementKey();
        node.setKeepSingleElementKey(keepSingleElementKey);

        Map<String, Object> processedData = processSourceParamWhenTargetIsObject(
                config, context.getCurrentData());

        node.setChildren(buildPreParamTree(allNodes, config.getConfigId(),
                processedData, context.getCurrentData(), context.getRootData()));
    }

    private Map<String, Object> processSourceParamWhenTargetIsObject(NodeParamConfig config, Map<String, Object> businessData) {
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
    protected void processPreArrayNodeType(NodeParamConfig config,
                                           List<NodeParamConfig> allNodes,
                                           PreBuildContext context,
                                           ParamTreeNode node) {
        doProcessPreArrayNodeType(config, allNodes, context, node);
    }

    @Override
    protected void processPostObjectNodeType(NodeParamConfig config,
                                             List<NodeParamConfig> allNodes,
                                             PostBuildContext context,
                                             ParamTreeNode node) {
        // 配置项:当对象元素为单个是否保留key
        Boolean keepSingleElementKey = config.getKeepSingleElementKey();
        node.setKeepSingleElementKey(keepSingleElementKey);
        MappingSource mappingSource = config.getMappingSource();
        if (mappingSource == null) {
            node.setChildren(buildPostParamTree(allNodes,
                    config.getConfigId(),
                    context.getCurrentInputData(),
                    context.getCurrentResponseData(),
                    context.getRootInputData(),
                    context.getRootResponseData()));
        } else {
            // 只要在确定了需要映射源数据源的时候才会进行处理
            Map<String, Object> sourceData = mappingSource == MappingSource.INPUT ?
                    context.getCurrentInputData() : context.getCurrentResponseData();
            Map<String, Object> targetBusinessData = processSourceParamWhenTargetIsObject(config, sourceData);
            node.setChildren(buildPostParamTree(allNodes,
                    config.getConfigId(),
                    targetBusinessData,
                    targetBusinessData,
                    context.getRootInputData(),
                    context.getRootResponseData()));
        }
    }

    @Override
    protected void processPostArrayNodeType(NodeParamConfig config,
                                            List<NodeParamConfig> allNodes,
                                            PostBuildContext context,
                                            ParamTreeNode node) {
        doProcessPostArrayNodeType(config, allNodes, context, node);
    }

    private void doProcessPostArrayNodeType(NodeParamConfig config,
                                            List<NodeParamConfig> allNodes,
                                            PostBuildContext context,
                                            ParamTreeNode node) {
        MappingSource mappingSource = config.getMappingSource();
        if (mappingSource == null || config.getSourceParamType() == ParamType.NONE) {
            node.setChildren(buildPostParamTree(allNodes,
                    config.getConfigId(),
                    context.getCurrentInputData(),
                    context.getCurrentResponseData(),
                    context.getRootInputData(),
                    context.getRootResponseData()));
        } else {
            Map<String, Object> targetBusinessData = mappingSource == MappingSource.INPUT ?
                    context.getCurrentInputData() : context.getCurrentResponseData();
            Object sourceValue = ProxyConvertHelper.convertSourceValue(config, targetBusinessData);
            if (config.getSourceParamType() == ParamType.OBJECT && sourceValue != null) {
                // 源参数是Object，包装成大小为1的数组
                List<ParamTreeNode> arrayChildren = buildPostParamTree(allNodes,
                        config.getConfigId(),
                        JsonUtils.toMap(sourceValue),
                        JsonUtils.toMap(sourceValue),
                        context.getRootInputData(),
                        context.getRootResponseData());
                node.setChildren(arrayChildren);
            } else if (config.getSourceParamType() == ParamType.ARRAY && sourceValue != null) {
                // 目标参数是数组,原参数可能是数组也可能是单个对象
                processPostArrayNodeTypeWhenSourceTypeIsArray(config, allNodes, sourceValue, context, node);
            }
        }
    }

    private void processPostArrayNodeTypeWhenSourceTypeIsArray(NodeParamConfig config,
                                                               List<NodeParamConfig> allNodes,
                                                               Object sourceValue,
                                                               PostBuildContext context,
                                                               ParamTreeNode node) {
        // 目标参数是数组,原参数可能是数组也可能是单个对象
        List<ParamTreeNode> allArrayChildren = new ArrayList<>();
        if (sourceValue instanceof List) {
            List<?> sourceList = (List<?>) sourceValue;
            Object o = sourceList.get(0);
            // 数组可以是基础数据类型和对象
            if (o instanceof Map) {
                for (Object item : sourceList) {
                    List<ParamTreeNode> nestedArrayChildren = buildPostParamTree(allNodes,
                            config.getConfigId(),
                            JsonUtils.toMap(item),
                            JsonUtils.toMap(item),
                            context.getRootInputData(),
                            context.getRootResponseData());
                    allArrayChildren.addAll(nestedArrayChildren);
                }
            } else {
                // 对于基础数据类型item是一个值,将目标映射key作为Map的key,item作为Map的value
                for (Object item : sourceList) {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put(config.getTargetParamKey(), item);
                    List<ParamTreeNode> nestedArrayChildren = buildPostParamTree(allNodes,
                            config.getConfigId(),
                            JsonUtils.toMap(itemMap),
                            JsonUtils.toMap(itemMap),
                            context.getRootInputData(),
                            context.getRootResponseData());
                    allArrayChildren.addAll(nestedArrayChildren);
                }
            }
        } else {
            // 其他情况下就是数组为一维的单个基础数据类型
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put(config.getTargetParamKey(), sourceValue);
            List<ParamTreeNode> nestedArrayChildren = buildPostParamTree(allNodes,
                    config.getConfigId(),
                    JsonUtils.toMap(itemMap),
                    JsonUtils.toMap(itemMap),
                    context.getRootInputData(),
                    context.getRootResponseData());
            allArrayChildren.addAll(nestedArrayChildren);
        }

        node.setChildren(allArrayChildren);
    }

    private void doProcessPreArrayNodeType(NodeParamConfig config,
                                           List<NodeParamConfig> allNodes,
                                           PreBuildContext context,
                                           ParamTreeNode node) {
        Object sourceValue = ProxyConvertHelper.convertSourceValue(config, context.getCurrentData());
        if (config.getSourceParamType() == ParamType.NONE) {
            node.setChildren(buildPreParamTree(allNodes,
                    config.getConfigId(),
                    context.getCurrentData(),
                    context.getParentData(),
                    context.getRootData()));

        } else if (config.getSourceParamType() == ParamType.OBJECT && sourceValue != null) {
            // 源参数是Object，包装成大小为1的数组
            List<ParamTreeNode> arrayChildren = buildPreParamTree(allNodes,
                    config.getConfigId(),
                    JsonUtils.toMap(sourceValue),
                    context.getCurrentData(),
                    context.getRootData());
            node.setChildren(arrayChildren);
        } else if (config.getSourceParamType() == ParamType.ARRAY && sourceValue != null) {
            // 目标参数是数组,原参数可能是数组也可能是单个对象
            processPreArrayNodeTypeWhenSourceTypeIsArray(config, allNodes, sourceValue, context, node);
        }
    }

    private void processPreArrayNodeTypeWhenSourceTypeIsArray(NodeParamConfig config,
                                                              List<NodeParamConfig> allNodes,
                                                              Object sourceValue,
                                                              PreBuildContext context,
                                                              ParamTreeNode node) {
        // 目标参数是数组,原参数可能是数组也可能是单个对象
        List<ParamTreeNode> allArrayChildren = new ArrayList<>();
        if (sourceValue instanceof List) {
            List<?> sourceList = (List<?>) sourceValue;
            Object o = sourceList.get(0);
            // 数组可以是基础数据类型和对象
            if (o instanceof Map) {
                for (Object item : sourceList) {
                    List<ParamTreeNode> nestedArrayChildren = buildPreParamTree(allNodes,
                            config.getConfigId(),
                            JsonUtils.toMap(item),
                            context.getCurrentData(),
                            context.getRootData());
                    allArrayChildren.addAll(nestedArrayChildren);
                }
            } else {
                // 对于基础数据类型item是一个值,将目标映射key作为Map的key,item作为Map的value
                for (Object item : sourceList) {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put(config.getTargetParamKey(), item);
                    List<ParamTreeNode> nestedArrayChildren = buildPreParamTree(allNodes,
                            config.getConfigId(),
                            JsonUtils.toMap(itemMap),
                            context.getCurrentData(),
                            context.getRootData());
                    allArrayChildren.addAll(nestedArrayChildren);
                }
            }
        } else {
            // 其他情况下就是数组为一维的单个基础数据类型
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put(config.getTargetParamKey(), sourceValue);
            List<ParamTreeNode> nestedArrayChildren = buildPreParamTree(allNodes,
                    config.getConfigId(),
                    JsonUtils.toMap(itemMap),
                    context.getCurrentData(),
                    context.getRootData());
            allArrayChildren.addAll(nestedArrayChildren);
        }

        node.setChildren(allArrayChildren);
    }
}
