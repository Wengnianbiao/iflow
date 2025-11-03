package com.helianhealth.agent.remote;

import com.helianhealth.agent.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import com.helianhealth.agent.remote.resolver.ValueResolverSelector;
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

    @Override
    public List<ParamTreeNode> buildParamTree(URL url,List<NodeParamConfigDO> configs,
                                              Integer parentId,
                                              Map<String, Object> businessData,
                                              Map<String, Object> parentBusinessData,
                                              Map<String, Object> sourceBusinessData) {
        return doBuildParamTree(configs, parentId, businessData, parentBusinessData, sourceBusinessData);
    }

    public List<ParamTreeNode> buildParamTree(List<NodeParamConfigDO> configs,
                                              Integer parentId,
                                              Map<String, Object> businessData,
                                              Map<String, Object> parentBusinessData,
                                              Map<String, Object> sourceBusinessData) {
        return doBuildParamTree(configs, parentId, businessData, parentBusinessData, sourceBusinessData);
    }

    public List<ParamTreeNode> doBuildParamTree(List<NodeParamConfigDO> configs,
                                                 Integer parentId,
                                                 Map<String, Object> businessData,
                                                 Map<String, Object> parentBusinessData,
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
                children.add(processParamNode(config, configs, businessData, parentBusinessData, sourceBusinessData));
            }
        } catch (Exception e) {
            log.error("Building param tree ERROR:", e);
        }

        return children;
    }

    private ParamTreeNode processParamNode(NodeParamConfigDO config,
                                           List<NodeParamConfigDO> allNodes,
                                           Map<String, Object> businessData,
                                           Map<String, Object> parentBusinessData,
                                           Map<String, Object> sourceBusinessData) {
        ParamTreeNode node = ParamTreeNode.builder()
                .paramKey(config.getTargetParamKey())
                .paramType(config.getTargetParamType())
                .build();

        // 根据节点类型处理
        switch (config.getTargetParamType()) {
            case OBJECT:
                // 对象类型处理
                processObjectNodeType(config, allNodes, businessData, parentBusinessData, sourceBusinessData, node);
                break;
            case ARRAY:
            case PURE_ARRAY:
                // 数组类型处理
                processArrayNodeType(config, allNodes, businessData, parentBusinessData, sourceBusinessData, node);
                break;
            default:
                // 基本类型：从业务数据中取值（根据mappingRule映射）
                node.setParamValue(resolveValue(config, businessData, parentBusinessData, sourceBusinessData));
        }

        return node;
    }

    private Object resolveValue(NodeParamConfigDO config,
                               Map<String, Object> businessData,
                               Map<String, Object> parentBusinessData,
                               Map<String, Object> sourceBusinessData) {
        return valueResolverSelector.resolveValue(config, businessData, parentBusinessData, sourceBusinessData);
    }

    protected abstract void processObjectNodeType(NodeParamConfigDO config,
                                               List<NodeParamConfigDO> allNodes,
                                               Map<String, Object> businessData,
                                               Map<String, Object> parentBusinessData,
                                               Map<String, Object> rootBusinessData,
                                               ParamTreeNode node);

    protected abstract void processArrayNodeType(NodeParamConfigDO config,
                                              List<NodeParamConfigDO> allNodes,
                                              Map<String, Object> businessData,
                                              Map<String, Object> parentBusinessData,
                                              Map<String, Object> rootBusinessData,
                                              ParamTreeNode node);
}
