package com.helianhealth.agent.invocation.remote;

import com.helianhealth.agent.workflow.enums.MappingSource;
import com.helianhealth.agent.workflow.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.workflow.model.dto.ParamTreeNode;
import com.helianhealth.agent.workflow.model.dto.PostBuildContext;
import com.helianhealth.agent.workflow.model.dto.PreBuildContext;
import com.helianhealth.agent.invocation.remote.resolver.ValueResolverSelector;
import com.helianhealth.agent.invocation.remote.support.BasicTypeConversionHandler;
import com.helianhealth.agent.invocation.remote.support.TypeHandler;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.URL;

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


    @Setter
    private ValueResolverSelector valueResolverSelector;

    private final TypeHandler typeHandler = new BasicTypeConversionHandler();

    @Override
    public List<ParamTreeNode> buildPreParamTree(URL url, 
                                                 List<NodeParamConfigDO> configs,
                                                 Integer parentId,
                                                 PreBuildContext context) {
        return doBuildPreParamTree(configs, parentId, context);
    }

    /**
     * 兼容旧接口的重载方法
     */
    public List<ParamTreeNode> buildPreParamTree(List<NodeParamConfigDO> configs,
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

    private List<ParamTreeNode> doBuildPreParamTree(List<NodeParamConfigDO> configs,
                                                    Integer parentId,
                                                    PreBuildContext context) {
        List<ParamTreeNode> children = new ArrayList<>();

        try {
            // 1、从root节点开始构建参数树,筛选当前父节点的子节点
            List<NodeParamConfigDO> currentNodes = configs.stream()
                    .filter(node -> Objects.equals(node.getParentId(), parentId))
                    .sorted(Comparator.comparingInt(NodeParamConfigDO::getSort))
                    .collect(Collectors.toList());

            // 2、组装每个节点的参数值
            for (NodeParamConfigDO config : currentNodes) {
                children.add(processPreParamNode(config, configs, context));
            }
        } catch (Exception e) {
            log.error("Building param tree ERROR:", e);
        }

        return children;
    }

    private ParamTreeNode processPreParamNode(NodeParamConfigDO config,
                                              List<NodeParamConfigDO> allNodes,
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

    private Object resolveValue(NodeParamConfigDO config,
                               Map<String, Object> businessData,
                               Map<String, Object> parentBusinessData,
                               Map<String, Object> sourceBusinessData) {
        return valueResolverSelector.resolveValue(config, businessData, parentBusinessData, sourceBusinessData);
    }

    protected abstract void processPreObjectNodeType(NodeParamConfigDO config,
                                                     List<NodeParamConfigDO> allNodes,
                                                     PreBuildContext context,
                                                     ParamTreeNode node);

    protected abstract void processPreArrayNodeType(NodeParamConfigDO config,
                                                    List<NodeParamConfigDO> allNodes,
                                                    PreBuildContext context,
                                                    ParamTreeNode node);

    @Override
    public List<ParamTreeNode> buildPostParamTree(URL url,
                                                  List<NodeParamConfigDO> configs,
                                                  Integer parentId,
                                                  PostBuildContext context){
        return doBuildPostParamTree(configs, parentId, context);
    }

    /**
     * 兼容旧接口的重载方法
     */
    public List<ParamTreeNode> buildPostParamTree(List<NodeParamConfigDO> configs,
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

    private List<ParamTreeNode> doBuildPostParamTree(List<NodeParamConfigDO> configs,
                                                     Integer parentId,
                                                     PostBuildContext context) {
        List<ParamTreeNode> children = new ArrayList<>();

        try {
            // 1、从root节点开始构建参数树,筛选当前父节点的子节点
            List<NodeParamConfigDO> currentNodes = configs.stream()
                    .filter(node -> Objects.equals(node.getParentId(), parentId))
                    .sorted(Comparator.comparingInt(NodeParamConfigDO::getSort))
                    .collect(Collectors.toList());

            // 2、组装每个节点的参数值
            for (NodeParamConfigDO config : currentNodes) {
                // 后置节点处理时会同时持有入参和响应的业务数据
                children.add(processPostParamNode(config, configs, context));
            }
        } catch (Exception e) {
            log.error("Building param tree ERROR:", e);
        }

        return children;
    }

    private ParamTreeNode processPostParamNode(NodeParamConfigDO config,
                                               List<NodeParamConfigDO> allNodes,
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

    protected abstract void processPostObjectNodeType(NodeParamConfigDO config,
                                                  List<NodeParamConfigDO> allNodes,
                                                  PostBuildContext context,
                                                  ParamTreeNode node);

    protected abstract void processPostArrayNodeType(NodeParamConfigDO config,
                                                 List<NodeParamConfigDO> allNodes,
                                                 PostBuildContext context,
                                                 ParamTreeNode node);
}
