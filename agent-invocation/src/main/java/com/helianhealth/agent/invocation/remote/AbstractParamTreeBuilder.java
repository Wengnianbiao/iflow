package com.helianhealth.agent.invocation.remote;

import com.helianhealth.agent.common.enums.MappingSource;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.invocation.remote.model.PostBuildContext;
import com.helianhealth.agent.invocation.remote.model.PreBuildContext;
import com.helianhealth.agent.invocation.remote.resolver.ValueResolverSelector;
import com.helianhealth.agent.invocation.remote.support.BasicTypeConversionHandler;
import com.helianhealth.agent.invocation.remote.support.TypeHandler;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 参数树构造器
 */
@Slf4j
public abstract class AbstractParamTreeBuilder implements ParamTree {

    private final ValueResolverSelector valueResolverSelector;
    private final TypeHandler typeHandler;

    public AbstractParamTreeBuilder(ValueResolverSelector valueResolverSelector) {
        this.valueResolverSelector = valueResolverSelector;
        this.typeHandler = new BasicTypeConversionHandler();
    }

    @Override
    public List<ParamTreeNode> buildPreParamTree(WorkflowNode flowNode,
                                                 List<NodeParamConfig> configs,
                                                 Integer parentId,
                                                 PreBuildContext buildContext) {
        return doBuildPreParamTree(configs, parentId, buildContext);
    }

    /**
     * 兼容旧接口的重载方法
     */
    public List<ParamTreeNode> buildPreParamTree(List<NodeParamConfig> configs,
                                                   Integer parentId,
                                                   Map<String, Object> businessData,
                                                   Map<String, Object> parentBusinessData,
                                                   Map<String, Object> sourceBusinessData){
        PreBuildContext context = PreBuildContext.builder()
                .currentData(businessData)
                .parentData(parentBusinessData)
                .rootData(sourceBusinessData)
                .build();
        return doBuildPreParamTree(configs, parentId, context);
    }

    private List<ParamTreeNode> doBuildPreParamTree(List<NodeParamConfig> configs,
                                                    Integer parentId,
                                                    PreBuildContext context) {
        List<ParamTreeNode> children = new ArrayList<>();

        try {
            // 1、从root节点开始构建参数树,筛选当前父节点的子节点
            List<NodeParamConfig> currentNodes = configs.stream()
                    .filter(node -> Objects.equals(node.getParentId(), parentId))
                    .sorted(Comparator.comparingInt(NodeParamConfig::getSort))
                    .collect(Collectors.toList());

            // 2、组装每个节点的参数值
            for (NodeParamConfig config : currentNodes) {
                children.add(processPreParamNode(config, configs, context));
            }
        } catch (Exception e) {
            log.error("Building param tree ERROR:", e);
        }

        return children;
    }

    private ParamTreeNode processPreParamNode(NodeParamConfig config,
                                              List<NodeParamConfig> allNodes,
                                              PreBuildContext context) {
        ParamTreeNode node = ParamTreeNode.builder()
                .paramKey(config.getTargetParamKey())
                .paramType(config.getTargetParamType())
                .build();

        // 根据节点类型处理
        switch (config.getTargetParamType()) {
            case OBJECT:
                // 对象类型处理
                processPreObjectNodeType(config, allNodes, context, node);
                break;
            case ARRAY:
            case PURE_ARRAY:
                // 数组类型处理
                processPreArrayNodeType(config, allNodes, context, node);
                break;
            default:
                // 基本类型：从业务数据中取值（根据mappingRule映射）并转化类型
                Object rawValue = resolveValue(config, context.getCurrentData(),
                        context.getParentData(), context.getRootData());
                Object convertedValue = typeHandler.convert(rawValue, config.getTargetParamType());
                node.setParamValue(convertedValue);
        }

        return node;
    }

    private Object resolveValue(NodeParamConfig config,
                               Map<String, Object> businessData,
                               Map<String, Object> parentBusinessData,
                               Map<String, Object> sourceBusinessData) {
        return valueResolverSelector.resolveValue(config, businessData, parentBusinessData, sourceBusinessData);
    }

    protected abstract void processPreObjectNodeType(NodeParamConfig config,
                                                     List<NodeParamConfig> allNodes,
                                                     PreBuildContext context,
                                                     ParamTreeNode node);

    protected abstract void processPreArrayNodeType(NodeParamConfig config,
                                                    List<NodeParamConfig> allNodes,
                                                    PreBuildContext context,
                                                    ParamTreeNode node);

    @Override
    public List<ParamTreeNode> buildPostParamTree(WorkflowNode flowNode,
                                                  List<NodeParamConfig> configs,
                                                  Integer parentId,
                                                  PostBuildContext buildContext){
        return doBuildPostParamTree(configs, parentId, buildContext);
    }

    /**
     * 兼容旧接口的重载方法
     */
    public List<ParamTreeNode> buildPostParamTree(List<NodeParamConfig> configs,
                                                   Integer parentId,
                                                   Map<String, Object> inputBusinessData,
                                                   Map<String, Object> responseBusinessData,
                                                   Map<String, Object> sourceInputBusinessData,
                                                   Map<String, Object> sourceResponseBusinessData) {
        PostBuildContext context = PostBuildContext.builder()
                .currentInputData(inputBusinessData)
                .currentResponseData(responseBusinessData)
                .rootInputData(sourceInputBusinessData)
                .rootResponseData(sourceResponseBusinessData)
                .build();
        return doBuildPostParamTree(configs, parentId, context);
    }

    private List<ParamTreeNode> doBuildPostParamTree(List<NodeParamConfig> configs,
                                                     Integer parentId,
                                                     PostBuildContext context) {
        List<ParamTreeNode> children = new ArrayList<>();

        try {
            // 1、从root节点开始构建参数树,筛选当前父节点的子节点
            List<NodeParamConfig> currentNodes = configs.stream()
                    .filter(node -> Objects.equals(node.getParentId(), parentId))
                    .sorted(Comparator.comparingInt(NodeParamConfig::getSort))
                    .collect(Collectors.toList());

            // 2、组装每个节点的参数值
            for (NodeParamConfig config : currentNodes) {
                // 后置节点处理时会同时持有入参和响应的业务数据
                children.add(processPostParamNode(config, configs, context));
            }
        } catch (Exception e) {
            log.error("Building param tree ERROR:", e);
        }

        return children;
    }

    private ParamTreeNode processPostParamNode(NodeParamConfig config,
                                               List<NodeParamConfig> allNodes,
                                               PostBuildContext context) {
        ParamTreeNode node = ParamTreeNode.builder()
                .paramKey(config.getTargetParamKey())
                .paramType(config.getTargetParamType())
                .build();

        // 根据节点类型处理
        switch (config.getTargetParamType()) {
            case OBJECT:
                // 对象类型处理
                processPostObjectNodeType(config, allNodes, context, node);
                break;
            case ARRAY:
            case PURE_ARRAY:
                // 数组类型处理
                processPostArrayNodeType(config, allNodes, context, node);
                break;
            default:
                // 基本类型：从业务数据中取值（根据mappingRule映射）
                Map<String, Object> dataSource = config.getMappingSource() == MappingSource.INPUT ?
                        context.getCurrentInputData() : context.getCurrentResponseData();
                node.setParamValue(resolveValue(config, dataSource,
                        context.getRootInputData(), context.getRootResponseData()));
        }

        return node;
    }

    protected abstract void processPostObjectNodeType(NodeParamConfig config,
                                                  List<NodeParamConfig> allNodes,
                                                  PostBuildContext context,
                                                  ParamTreeNode node);

    protected abstract void processPostArrayNodeType(NodeParamConfig config,
                                                 List<NodeParamConfig> allNodes,
                                                 PostBuildContext context,
                                                 ParamTreeNode node);
}
