package com.helianhealth.agent.ai.service;

import com.alibaba.fastjson2.JSON;
import com.helianhealth.agent.console.model.domain.InterfaceWorkflowDO;
import com.helianhealth.agent.console.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.console.service.InterfaceWorkflowNodeService;
import com.helianhealth.agent.console.service.InterfaceWorkflowService;
import com.helianhealth.agent.console.service.NodeParamConfigService;
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

    private final InterfaceWorkflowService workflowService;
    private final InterfaceWorkflowNodeService workflowNodeService;
    private final NodeParamConfigService nodeParamConfigService;

    /**
     * 从配置文件创建完整的工作流（工作流+节点+参数配置）
     */
    @Transactional("workflowAgentTransactionManager")
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
            InterfaceWorkflowDO workflow = config.getWorkflow();
            int flowId = workflowService.save(workflow);
            log.info("工作流创建成功, flowId={}, flowName={}", flowId, workflow.getFlowName());
            
            // 4. 创建节点
            WorkflowConfig.NodeConfig nodeConfig = config.getNode();
            nodeConfig.setFlowId(flowId);
            
            // 将NodeConfig转换为WorkflowNodeCreateReq所需的格式
            com.helianhealth.agent.console.controller.request.WorkflowNodeCreateReq nodeRequest = 
                new com.helianhealth.agent.console.controller.request.WorkflowNodeCreateReq();
            nodeRequest.setNodeName(nodeConfig.getNodeName());
            nodeRequest.setFlowId(flowId);
            nodeRequest.setNodeType(nodeConfig.getNodeType());
            nodeRequest.setParamFilterExpr(nodeConfig.getParamFilterExpr());
            nodeRequest.setMetaInfo(nodeConfig.getMetaInfo());
            nodeRequest.setScheduleExpr(nodeConfig.getScheduleExpr());
            nodeRequest.setScheduleParamSourceType(nodeConfig.getScheduleParamSourceType());
            nodeRequest.setKeywordField(nodeConfig.getKeywordField());
            nodeRequest.setSplitField(nodeConfig.getSplitField());
            nodeRequest.setRemoteExtension(nodeConfig.getRemoteExtension());
            
            int nodeId = workflowNodeService.save(nodeRequest);
            log.info("节点创建成功, nodeId={}, nodeName={}", nodeId, nodeConfig.getNodeName());
            
            // 5. 批量创建参数配置
            List<NodeParamConfigDO> paramsConfig = config.getParamsConfig();
            int paramCount = 0;
            for (NodeParamConfigDO paramConfig : paramsConfig) {
                paramConfig.setNodeId(nodeId);
                nodeParamConfigService.save(paramConfig);
                paramCount++;
            }
            log.info("参数配置创建成功, 共创建{}个参数", paramCount);
            
            return new WorkflowCreationResult(flowId, nodeId, paramCount, 
                "工作流创建成功: " + workflow.getFlowName());
                
        } catch (IOException e) {
            log.error("读取配置文件失败: {}", configFileName, e);
            throw new RuntimeException("读取配置文件失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("从配置文件创建工作流失败: {}", configFileName, e);
            throw new RuntimeException("创建工作流失败: " + e.getMessage());
        }
    }

    /**
     * 工作流配置结构
     */
    @Data
    public static class WorkflowConfig {
        private InterfaceWorkflowDO workflow;
        private NodeConfig node;
        private List<NodeParamConfigDO> paramsConfig;
        
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
}
