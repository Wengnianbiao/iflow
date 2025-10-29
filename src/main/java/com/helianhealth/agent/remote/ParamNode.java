package com.helianhealth.agent.remote;

import com.helianhealth.agent.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

import java.util.List;
import java.util.Map;

@SPI("default")
public interface ParamNode {

    @Adaptive("node")
    List<ParamTreeNode> buildParamTree(URL url,
                                       List<NodeParamConfigDO> configs,
                                       Integer parentId,
                                       Map<String, Object> businessData,
                                       Map<String, Object> sourceBusinessData);
}
