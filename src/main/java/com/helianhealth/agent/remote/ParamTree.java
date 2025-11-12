package com.helianhealth.agent.remote;

import com.helianhealth.agent.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

import java.util.List;
import java.util.Map;

@SPI("default")
public interface ParamTree {

    @Adaptive("node")
    List<ParamTreeNode> buildPreParamTree(URL url,
                                          List<NodeParamConfigDO> configs,
                                          Integer parentId,
                                          Map<String, Object> businessData,
                                          Map<String, Object> parentBusinessData,
                                          Map<String, Object> sourceBusinessData);

    @Adaptive("node")
    List<ParamTreeNode> buildPostParamTree(URL url,
                                          List<NodeParamConfigDO> configs,
                                          Integer parentId,
                                          Map<String, Object> inputBusinessData,
                                          Map<String, Object> responseBusinessData,
                                          Map<String, Object> sourceInputBusinessData,
                                          Map<String, Object> sourceResponseBusinessData);
}
