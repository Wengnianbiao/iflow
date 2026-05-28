package com.helianhealth.agent.repository.support;

import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;

/**
 * 节点参数配置写入前规范化。
 */
public final class NodeParamConfigNormalizer {

    private NodeParamConfigNormalizer() {
    }

    public static void normalize(NodeParamConfig config) {
        if (config == null) {
            return;
        }
        if (config.getSort() == null) {
            config.setSort(1);
        }
        if (config.getSourceParamType() == ParamType.NONE) {
            config.setSourceParamKey(null);
        }
        if (config.getProcessType() == ProcessType.PRE_PROCESS) {
            config.setMappingSource(null);
        }
    }
}
