package com.helianhealth.agent.remote.database;

import com.helianhealth.agent.enums.MappingType;
import com.helianhealth.agent.enums.ParamType;
import com.helianhealth.agent.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import com.helianhealth.agent.remote.ParamNodeHandler;
import com.helianhealth.agent.remote.ProxyConvertHelper;
import com.helianhealth.agent.utils.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class DatabaseParamNode extends ParamNodeHandler {
    @Override
    public void processObjectNodeType(NodeParamConfigDO config, List<NodeParamConfigDO> allNodes, Map<String, Object> businessData, Map<String, Object> rootBusinessData, ParamTreeNode node) {
        node.setChildren(buildParamTree(allNodes,
                config.getConfigId(),
                processSourceParamWhenTargetIsObject(config, businessData),
                rootBusinessData));
    }


    @Override
    public void processArrayNodeType(NodeParamConfigDO config, List<NodeParamConfigDO> allNodes, Map<String, Object> businessData, Map<String, Object> rootBusinessData, ParamTreeNode node) {
        doProcessArrayNodeType(config, allNodes, businessData, rootBusinessData, node);
    }

    /**
     * database模式下的数组构建规则会存在直接映射的场景
     * @param config 节点参数配置
     * @param allNodes 所有节点参数配置
     * @param businessData 业务数据
     * @param rootBusinessData 根业务数据
     * @param node 当前节点
     */
    private void doProcessArrayNodeType(NodeParamConfigDO config, List<NodeParamConfigDO> allNodes, Map<String, Object> businessData, Map<String, Object> rootBusinessData, ParamTreeNode node) {
        // 根据映射源进入迭代
        Object sourceValue = ProxyConvertHelper.convertSourceValue(config, businessData, rootBusinessData);

        if (config.getSourceParamType() == ParamType.OBJECT && sourceValue != null) {
            // 源参数是Object，包装成大小为1的数组
            List<ParamTreeNode> arrayChildren = buildParamTree(allNodes,
                    config.getConfigId(),
                    JsonUtils.toMap(sourceValue),
                    rootBusinessData);
            node.setChildren(arrayChildren);
        } else if (config.getSourceParamType() == ParamType.ARRAY ||
                config.getSourceParamType() == ParamType.PURE_ARRAY
                        && sourceValue != null) {
            // 直接映射的场景直接赋值
            if (config.getMappingType().equals(MappingType.DIRECT)) {
                node.setParamValue(sourceValue);
            } else {
                List<ParamTreeNode> allArrayChildren = new ArrayList<>();
                if (sourceValue instanceof List) {
                    List<?> sourceList = (List<?>) sourceValue;
                    for (Object item : sourceList) {
                        List<ParamTreeNode> arrayChildren = buildParamTree(allNodes,
                                config.getConfigId(),
                                JsonUtils.toMap(item),
                                rootBusinessData);
                        allArrayChildren.addAll(arrayChildren);
                    }
                }

                node.setChildren(allArrayChildren);
            }
        } else {
            // 其他情况创建空数组
            node.setChildren(new ArrayList<>());
        }
    }
}
