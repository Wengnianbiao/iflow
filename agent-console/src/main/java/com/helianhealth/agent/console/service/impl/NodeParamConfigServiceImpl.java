package com.helianhealth.agent.console.service.impl;

import com.alibaba.fastjson2.JSON;
import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.console.model.dto.NodeParamConfigImportDTO;
import com.helianhealth.agent.console.service.NodeParamConfigService;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.service.NodeParamConfigCommandService;
import com.helianhealth.agent.repository.service.NodeParamConfigQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class NodeParamConfigServiceImpl implements NodeParamConfigService {

    private final NodeParamConfigQueryService nodeParamConfigQueryService;
    private final NodeParamConfigCommandService nodeParamConfigCommandService;

    public NodeParamConfigServiceImpl(NodeParamConfigQueryService nodeParamConfigQueryService,
                                      NodeParamConfigCommandService nodeParamConfigCommandService) {
        this.nodeParamConfigQueryService = nodeParamConfigQueryService;
        this.nodeParamConfigCommandService = nodeParamConfigCommandService;
    }

    @Override
    public List<NodeParamConfig> selectPreProcessConfigByNodeId(Integer nodeId) {
        return nodeParamConfigQueryService.selectPreProcessConfigByNodeId(nodeId);
    }

    @Override
    public List<NodeParamConfig> selectPostProcessConfigByNodeId(Integer nodeId) {
        return nodeParamConfigQueryService.selectPostProcessConfigByNodeId(nodeId);
    }

    @Override
    public PageList<NodeParamConfig> findAllNodeParamConfigs(Integer nodeId, String nodeName, ProcessType processType, String targetParam, int pageNum, int pageSize) {
        try {
            String processTypeName = processType != null ? processType.name() : null;
            return nodeParamConfigQueryService.selectAllConfigsPaged(nodeId, nodeName, processTypeName, targetParam, pageNum, pageSize);
        } catch (Exception e) {
            log.error("获取节点参数配置列表失败", e);
            throw new AgentException(AgentErrorCode.CONSOLE_OPERATION_FAILED, "获取节点参数配置列表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<NodeParamConfig> findAllNodeParamConfigsUnpaged(Integer nodeId, ProcessType processType) {
        String processTypeName = processType != null ? processType.name() : null;
        return nodeParamConfigQueryService.selectAllConfigs(nodeId, null, processTypeName, null);
    }

    @Override
    public NodeParamConfig update(NodeParamConfig config) {
        try {
            if (config == null || config.getConfigId() == null) {
                throw new IllegalArgumentException("参数不能为空，configId 必须存在");
            }
            // 原参数类型为 null 时，将原参数名设置为null
            if (config.getSourceParamType() == ParamType.NONE) {
                config.setSourceParamKey(null);
            }
            // 为sort赋予默认值为1
            if (config.getSort() == null) {
                config.setSort(1);
            }

            int result = nodeParamConfigCommandService.updateConfig(config);

            if (result > 0) {
                return nodeParamConfigQueryService.selectConfigById(config.getConfigId());
            } else {
                log.warn("更新节点参数配置失败，未找到对应记录: configId={}", config.getConfigId());
                return null;
            }
        } catch (Exception e) {
            log.error("更新节点参数配置失败", e);
            throw new AgentException(AgentErrorCode.CONSOLE_OPERATION_FAILED, "更新节点参数配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public NodeParamConfig save(NodeParamConfig config) {
        try {
            if (config == null) {
                throw new IllegalArgumentException("参数不能为空");
            }
            // 为sort赋予默认值为1
            if (config.getSort() == null) {
                config.setSort(1);
            }

            int result = nodeParamConfigCommandService.insertConfig(config);

            if (result > 0) {
                return config;
            } else {
                log.warn("创建节点参数配置失败");
                return null;
            }
        } catch (Exception e) {
            log.error("创建节点参数配置失败", e);
            throw new AgentException(AgentErrorCode.CONSOLE_OPERATION_FAILED, "创建节点参数配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteById(Integer configId) {
        try {
            if (configId == null) {
                throw new IllegalArgumentException("参数不能为空，configId 必须存在");
            }
            nodeParamConfigCommandService.deleteConfig(configId);
        } catch (Exception e) {
            log.error("删除节点参数配置失败", e);
            throw new AgentException(AgentErrorCode.CONSOLE_OPERATION_FAILED, "删除节点参数配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void importNodeParamConfig(Integer nodeId, MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("文件不能为空");
            }

            String fileName = file.getOriginalFilename();
            if (!StringUtils.hasText(fileName) || !fileName.toLowerCase().endsWith(".json")) {
                throw new IllegalArgumentException("文件格式不正确，仅支持JSON格式文件");
            }

            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            NodeParamConfigImportDTO importDTO = JSON.parseObject(content, NodeParamConfigImportDTO.class);

            if (importDTO == null) {
                throw new IllegalArgumentException("JSON文件格式不正确");
            }

            if (importDTO.getParamsConfig() == null || importDTO.getParamsConfig().isEmpty()) {
                log.error("JSON文件格式不正确，缺少参数配置");
                return;
            }

            List<NodeParamConfigImportDTO.NodeParamConfigDTO> paramsConfig =
                    importDTO.getParamsConfig();

            int successCount = 0;

            for (NodeParamConfigImportDTO.NodeParamConfigDTO config : paramsConfig) {
                successCount += processConfigRecursive(config, nodeId, null);
            }

            log.info("成功导入{}条参数配置", successCount);
        } catch (IOException e) {
            log.error("读取文件失败", e);
            throw new AgentException(AgentErrorCode.CONSOLE_OPERATION_FAILED, "读取文件失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("导入节点参数配置失败", e);
            throw new AgentException(AgentErrorCode.CONSOLE_OPERATION_FAILED, "导入节点参数配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void importNodeParamConfig(Integer nodeId, List<NodeParamConfigImportDTO.NodeParamConfigDTO> paramsConfig) {
        try {
            int successCount = 0;

            if (paramsConfig == null || paramsConfig.isEmpty()) {
                return;
            }

            for (NodeParamConfigImportDTO.NodeParamConfigDTO config : paramsConfig) {
                successCount += processConfigRecursive(config, nodeId, null);
            }

            log.info("成功导入{}条参数配置", successCount);
        } catch (Exception e) {
            log.error("导入节点参数配置失败", e);
            throw new AgentException(AgentErrorCode.CONSOLE_OPERATION_FAILED, "导入节点参数配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 递归处理参数配置及其子参数
     */
    private int processConfigRecursive(
            NodeParamConfigImportDTO.NodeParamConfigDTO paramConfigDTO,
            Integer nodeId,
            Integer parentId) {

        // 1. 将DTO转换为领域对象
        NodeParamConfig config = convertToDomain(paramConfigDTO, nodeId, parentId);

        // 2. 保存配置
        NodeParamConfig savedConfig = save(config);
        int count = 1;

        // 3. 递归处理子参数
        if (paramConfigDTO.getChildParam() != null && !paramConfigDTO.getChildParam().isEmpty()) {
            for (NodeParamConfigImportDTO.NodeParamConfigDTO child : paramConfigDTO.getChildParam()) {
                count += processConfigRecursive(child, nodeId, savedConfig.getConfigId());
            }
        }

        return count;
    }

    /**
     * 将导入DTO转换为领域对象
     */
    private NodeParamConfig convertToDomain(
            NodeParamConfigImportDTO.NodeParamConfigDTO paramConfigDTO,
            Integer nodeId,
            Integer parentId) {

        return NodeParamConfig.builder()
                .processType(paramConfigDTO.getProcessType())
                .nodeId(nodeId)
                .parentId(parentId)
                .sourceParamKey(paramConfigDTO.getSourceParamKey())
                .sourceParamType(paramConfigDTO.getSourceParamType())
                .paramDesc(paramConfigDTO.getParamDesc())
                .targetParamKey(paramConfigDTO.getTargetParamKey())
                .targetParamType(paramConfigDTO.getTargetParamType())
                .keepSingleElementKey(paramConfigDTO.getKeepSingleElementKey())
                .sort(paramConfigDTO.getSort())
                .mappingType(paramConfigDTO.getMappingType())
                .mappingSource(paramConfigDTO.getMappingSource())
                .mappingRule(paramConfigDTO.getMappingRule())
                .build();
    }
}
