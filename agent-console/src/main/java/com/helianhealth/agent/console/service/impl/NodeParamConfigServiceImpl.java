package com.helianhealth.agent.console.service.impl;

import com.alibaba.fastjson2.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.repository.mapper.NodeParamConfigMapper;
import com.helianhealth.agent.repository.model.po.NodeParamConfigDO;
import com.helianhealth.agent.console.model.dto.NodeParamConfigImportDTO;
import com.helianhealth.agent.console.service.NodeParamConfigService;
import com.helianhealth.agent.repository.service.NodeParamConfigCommandService;
import com.helianhealth.agent.repository.service.impl.NodeParamConfigQueryServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class NodeParamConfigServiceImpl extends NodeParamConfigQueryServiceImpl implements NodeParamConfigService {

    private final NodeParamConfigCommandService nodeParamConfigCommandService;

    public NodeParamConfigServiceImpl(NodeParamConfigMapper nodeParamConfigMapper,
                                      NodeParamConfigCommandService nodeParamConfigCommandService) {
        super(nodeParamConfigMapper);
        this.nodeParamConfigCommandService = nodeParamConfigCommandService;
    }

    @Override
    public PageList<NodeParamConfigDO> findAllNodeParamConfigs(Integer nodeId, ProcessType processType, String targetParam, int pageNum, int pageSize) {
        try {
            // 开启分页
            PageHelper.startPage(pageNum, pageSize);

            // 查询数据
            String processTypeName = processType != null ? processType.name() : null;
            List<NodeParamConfigDO> configs = nodeParamConfigCommandService.selectAllConfigs(nodeId, processTypeName, targetParam);

            // 将查询结果转换为 PageInfo 对象
            PageInfo<NodeParamConfigDO> pageInfo = new PageInfo<>(configs);

            // 构建 PageList 对象
            PageList<NodeParamConfigDO> pageList = new PageList<>();
            pageList.setRows(configs);
            pageList.setTotal(pageInfo.getTotal());
            pageList.setPageNum(pageInfo.getPageNum());
            pageList.setPageSize(pageInfo.getPageSize());

            return pageList;
        } catch (Exception e) {
            log.error("获取节点参数配置列表失败", e);
            throw new RuntimeException("获取节点参数配置列表失败: " + e.getMessage());
        }
    }

    @Override
    public List<NodeParamConfigDO> findAllNodeParamConfigsUnpaged(Integer nodeId, ProcessType processType) {
        // 查询数据
        String processTypeName = processType != null ? processType.name() : null;

        return nodeParamConfigCommandService.selectAllConfigs(nodeId, processTypeName, null);
    }

    @Override
    public NodeParamConfigDO update(NodeParamConfigDO config) {
        try {
            // 验证参数
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

            // 执行更新操作
            int result = nodeParamConfigCommandService.updateConfig(config);

            if (result > 0) {
                return nodeParamConfigCommandService.selectConfigById(config.getConfigId());
            } else {
                log.warn("更新节点参数配置失败，未找到对应记录: configId={}", config.getConfigId());
                return null;
            }
        } catch (Exception e) {
            log.error("更新节点参数配置失败", e);
            throw new RuntimeException("更新节点参数配置失败: " + e.getMessage());
        }
    }

    @Override
    public NodeParamConfigDO save(NodeParamConfigDO config) {
        try {
            // 验证参数
            if (config == null) {
                throw new IllegalArgumentException("参数不能为空");
            }
            // 为sort赋予默认值为1
            if (config.getSort() == null) {
                config.setSort(1);
            }

            // 执行插入操作
            int result = nodeParamConfigCommandService.insertConfig(config);

            if (result > 0) {
                // 如果使用自增主键，MyBatis会自动将生成的ID设置到config对象中
                return config;
            } else {
                log.warn("创建节点参数配置失败");
                return null;
            }
        } catch (Exception e) {
            log.error("创建节点参数配置失败", e);
            throw new RuntimeException("创建节点参数配置失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteById(Integer configId) {
        try {
            // 验证参数
            if (configId == null) {
                throw new IllegalArgumentException("参数不能为空，configId 必须存在");
            }

            // 执行删除操作
            nodeParamConfigCommandService.deleteConfig(configId);
        } catch (Exception e) {
            log.error("删除节点参数配置失败", e);
            throw new RuntimeException("删除节点参数配置失败: " + e.getMessage());
        }
    }

    @Override
    public void importNodeParamConfig(Integer nodeId, MultipartFile file) {
        try {
            // 1. 校验文件格式
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("文件不能为空");
            }

            String fileName = file.getOriginalFilename();
            if (!StringUtils.hasText(fileName) || !fileName.toLowerCase().endsWith(".json")) {
                throw new IllegalArgumentException("文件格式不正确，仅支持JSON格式文件");
            }

            // 2. 解析JSON文件
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            NodeParamConfigImportDTO importDTO = JSON.parseObject(content, NodeParamConfigImportDTO.class);

            if (importDTO == null) {
                throw new IllegalArgumentException("JSON文件格式不正确");
            }

            if (importDTO.getParamsConfig() == null || importDTO.getParamsConfig().isEmpty()) {
                log.error("JSON文件格式不正确，缺少参数配置");
                return;
            }

            // 3. 处理参数配置并批量插入（递归处理父子关系）
            List<NodeParamConfigImportDTO.NodeParamConfigDTO> paramsConfig =
                    importDTO.getParamsConfig();

            int successCount = 0;

            // 递归处理参数配置
            for (NodeParamConfigImportDTO.NodeParamConfigDTO config : paramsConfig) {
                successCount += processConfigRecursive(config, nodeId, null);
            }

            log.info("成功导入{}条参数配置", successCount);
        } catch (IOException e) {
            log.error("读取文件失败", e);
            throw new RuntimeException("读取文件失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("导入节点参数配置失败", e);
            throw new RuntimeException("导入节点参数配置失败: " + e.getMessage());
        }
    }

    @Override
    public void importNodeParamConfig(Integer nodeId, List<NodeParamConfigImportDTO.NodeParamConfigDTO> paramsConfig) {
        try {
            int successCount = 0;

            if (paramsConfig == null || paramsConfig.isEmpty()) {
                return;
            }

            // 递归处理参数配置
            for (NodeParamConfigImportDTO.NodeParamConfigDTO config : paramsConfig) {
                successCount += processConfigRecursive(config, nodeId, null);
            }

            log.info("成功导入{}条参数配置", successCount);
        } catch (Exception e) {
            log.error("导入节点参数配置失败", e);
            throw new RuntimeException("导入节点参数配置失败: " + e.getMessage());
        }
    }

    /**
     * 递归处理参数配置及其子参数
     *
     * @param paramConfigDTO 导出的参数配置DTO
     * @param nodeId         节点ID
     * @param parentId       父参数配置ID
     * @return 处理的参数配置数量
     */
    private int processConfigRecursive(
            NodeParamConfigImportDTO.NodeParamConfigDTO paramConfigDTO,
            Integer nodeId,
            Integer parentId) {

        // 1. 将DTO转换为DO
        NodeParamConfigDO configDO = convertToDO(paramConfigDTO, nodeId, parentId);

        // 2. 保存配置
        NodeParamConfigDO savedConfig = save(configDO);
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
     * 将NodeParamConfigExportDTO转换为NodeParamConfigDO
     *
     * @param paramConfigDTO NodeParamConfigExportDTO对象
     * @param nodeId         节点ID
     * @param parentId       父参数配置ID
     * @return NodeParamConfigDO对象
     */
    private NodeParamConfigDO convertToDO(
            NodeParamConfigImportDTO.NodeParamConfigDTO paramConfigDTO,
            Integer nodeId,
            Integer parentId) {

        return NodeParamConfigDO.builder()
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
