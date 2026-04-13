package com.helianhealth.agent.ai.service;

import com.alibaba.fastjson2.JSON;
import com.helianhealth.agent.common.enums.NodeType;
import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.enums.ScheduleParamSourceType;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.service.NodeParamConfigCommandService;
import com.helianhealth.agent.repository.service.WorkflowCommandService;
import com.helianhealth.agent.repository.service.WorkflowNodeCommandService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

/**
 * MCP配置服务 - 支持从配置文件创建完整工作流
 */
@Service
@Slf4j
@AllArgsConstructor
public class McpConfigService {

    private final WorkflowCommandService workflowCommandService;
    private final WorkflowNodeCommandService workflowNodeCommandService;
    private final NodeParamConfigCommandService nodeParamConfigCommandService;

    /**
     * 从配置文件创建完整的工作流（工作流+节点+参数配置）
     */
    @Transactional("repositoryAgentTransactionManager")
    public WorkflowCreationResult createFromConfigFile(String configFileName) {
        try {
            log.info("开始从配置文件创建工作流: {}", configFileName);
            
            // 1. 读取配置文件
            ClassPathResource resource = new ClassPathResource(configFileName);
            InputStream inputStream = resource.getInputStream();
            Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name());
            String content = scanner.useDelimiter("\\A").next();
            scanner.close();
            inputStream.close();
            
            // 2. 解析配置
            WorkflowConfig config = JSON.parseObject(content, WorkflowConfig.class);
            
            // 3. 创建工作流
            Workflow workflow = config.getWorkflow();
            workflowCommandService.createWorkflow(workflow);
            Integer flowId = workflow.getFlowId();
            log.info("工作流创建成功, flowId={}, flowName={}", flowId, workflow.getFlowName());
            
            // 4. 创建节点
            WorkflowConfig.NodeConfig nodeConfig = config.getNode();
            nodeConfig.setFlowId(flowId);
            
            WorkflowNode nodeRequest = WorkflowNode.builder()
                    .nodeName(nodeConfig.getNodeName())
                    .flowId(flowId)
                    .nodeType(parseNodeType(nodeConfig.getNodeType()))
                    .paramFilterExpr(nodeConfig.getParamFilterExpr())
                    .metaInfo(nodeConfig.getMetaInfo())
                    .scheduleExpr(nodeConfig.getScheduleExpr())
                    .scheduleParamSourceType(parseScheduleParamSourceType(nodeConfig.getScheduleParamSourceType()))
                    .keywordField(nodeConfig.getKeywordField())
                    .splitField(nodeConfig.getSplitField())
                    .remoteExtension(nodeConfig.getRemoteExtension())
                    .build();
            
            workflowNodeCommandService.createNode(nodeRequest);
            Integer nodeId = nodeRequest.getNodeId();
            log.info("节点创建成功, nodeId={}, nodeName={}", nodeId, nodeConfig.getNodeName());
            
            // 5. 批量创建参数配置
            List<NodeParamConfig> paramsConfig = config.getParamsConfig();
            int paramCount = 0;
            for (NodeParamConfig paramConfig : paramsConfig) {
                paramConfig.setNodeId(nodeId);
                normalizeConfig(paramConfig);
                nodeParamConfigCommandService.insertConfig(paramConfig);
                paramCount++;
            }
            log.info("参数配置创建成功, 共创建{}个参数", paramCount);
            
            return new WorkflowCreationResult(flowId, nodeId, paramCount, 
                "工作流创建成功: " + workflow.getFlowName());
                
        } catch (IOException e) {
            log.error("读取配置文件失败: {}", configFileName, e);
            throw new AgentException(AgentErrorCode.CONSOLE_OPERATION_FAILED, "读取配置文件失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("从配置文件创建工作流失败: {}", configFileName, e);
            throw new AgentException(AgentErrorCode.CONSOLE_OPERATION_FAILED, "创建工作流失败: " + e.getMessage(), e);
        }
    }

    /**
     * 工作流配置结构
     */
    @Data
    public static class WorkflowConfig {
        private Workflow workflow;
        private NodeConfig node;
        private List<NodeParamConfig> paramsConfig;
        
        @Data
        public static class NodeConfig {
            private String nodeName;
            private Integer flowId;
            private String nodeType;
            private String paramFilterExpr;
            private String metaInfo;
            private String scheduleExpr;
            private String scheduleParamSourceType;
            private String keywordField;
            private String splitField;
            private String remoteExtension;
        }
    }

    /**
     * 工作流创建结果
     */
    @Data
    @AllArgsConstructor
    public static class WorkflowCreationResult {
        private Integer flowId;
        private Integer nodeId;
        private Integer paramCount;
        private String message;
    }

    private NodeType parseNodeType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return NodeType.valueOf(value.trim().toUpperCase());
    }

    private ScheduleParamSourceType parseScheduleParamSourceType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return ScheduleParamSourceType.valueOf(value.trim().toUpperCase());
    }

    private void normalizeConfig(NodeParamConfig config) {
        if (config.getSort() == null) {
            config.setSort(1);
        }
        if (config.getSourceParamType() == ParamType.NONE) {
            config.setSourceParamKey(null);
        }
    }
}
