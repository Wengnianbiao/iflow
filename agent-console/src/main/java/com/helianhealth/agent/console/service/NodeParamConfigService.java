package com.helianhealth.agent.console.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.console.model.dto.NodeParamConfigImportDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface NodeParamConfigService {

    PageList<NodeParamConfig> findAllNodeParamConfigs(Integer nodeId, String nodeName, ProcessType processType, String targetParam, int pageNum, int pageSize);

    List<NodeParamConfig> findAllNodeParamConfigsUnpaged(Integer nodeId, ProcessType processType);

    NodeParamConfig update(NodeParamConfig config);

    NodeParamConfig save(NodeParamConfig config);

    void deleteById(Integer configId);

    void importNodeParamConfig(Integer nodeId, MultipartFile file);

    void importNodeParamConfig(Integer nodeId, List<NodeParamConfigImportDTO.NodeParamConfigDTO> paramsConfig);
}
