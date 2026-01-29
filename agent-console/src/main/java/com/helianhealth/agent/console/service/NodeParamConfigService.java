package com.helianhealth.agent.console.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.console.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.console.model.dto.NodeParamConfigImportDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface NodeParamConfigService {

    PageList<NodeParamConfigDO> findAllNodeParamConfigs(Integer nodeId, ProcessType processType,String targetParam, int pageNum, int pageSize);

    List<NodeParamConfigDO> findAllNodeParamConfigsUnpaged(Integer nodeId, ProcessType processType);

    NodeParamConfigDO update(NodeParamConfigDO config);

    NodeParamConfigDO save(NodeParamConfigDO config);

    void deleteById(Integer configId);

    void importNodeParamConfig(Integer nodeId, MultipartFile file);

    void importNodeParamConfig(Integer nodeId, List<NodeParamConfigImportDTO.NodeParamConfigDTO> paramsConfig);

    List<NodeParamConfigDO> selectPreProcessConfigByNodeId(Integer nodeId);

    List<NodeParamConfigDO> selectPostProcessConfigByNodeId(Integer nodeId);
}
