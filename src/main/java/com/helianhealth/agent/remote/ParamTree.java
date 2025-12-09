package com.helianhealth.agent.remote;

import com.helianhealth.agent.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import com.helianhealth.agent.model.dto.PostBuildContext;
import com.helianhealth.agent.model.dto.PreBuildContext;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

import java.util.List;

/**
 * 参数树构建器
 * 负责将配置化的参数映射规则转换为实际的参数树结构
 */
@SPI("default")
public interface ParamTree {

    /**
     * 构建前置参数树（调用前的参数转换）
     * @param url Dubbo URL（用于 SPI 适配）
     * @param configs 参数配置列表
     * @param parentId 父节点 ID（用于递归构建）
     * @param context 前置构建上下文，封装了所有需要的数据源
     * @return 构建好的参数树节点列表
     */
    @Adaptive("node")
    List<ParamTreeNode> buildPreParamTree(URL url,
                                          List<NodeParamConfigDO> configs,
                                          Integer parentId,
                                          PreBuildContext context);

    /**
     * 构建后置参数树（调用后的响应转换）
     * @param url Dubbo URL（用于 SPI 适配）
     * @param configs 参数配置列表
     * @param parentId 父节点 ID（用于递归构建）
     * @param context 后置构建上下文，封装了输入和响应两份数据源
     * @return 构建好的参数树节点列表
     */
    @Adaptive("node")
    List<ParamTreeNode> buildPostParamTree(URL url,
                                           List<NodeParamConfigDO> configs,
                                           Integer parentId,
                                           PostBuildContext context);
}
