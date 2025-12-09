package com.helianhealth.agent.controller.mock;

import com.helianhealth.agent.common.PageList;
import com.helianhealth.agent.common.ResultData;
import com.helianhealth.agent.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.model.domain.MockConfigDO;
import com.helianhealth.agent.schedule.build.WorkFlowNodeBuilder;
import com.helianhealth.agent.service.InterfaceWorkflowNodeService;
import com.helianhealth.agent.service.MockConfigService;
import com.helianhealth.agent.utils.ResponseRenderUtils;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Mock参数管理控制器
 * 提供Mock配置的增删改查接口
 */
@RestController
@RequestMapping("/v1/console/mock")
@Slf4j
@AllArgsConstructor
public class MockController {

    private final MockConfigService mockConfigService;
    private final InterfaceWorkflowNodeService workflowNodeService;
    private final WorkFlowNodeBuilder workFlowNodeBuilder;

    /**
     * 分页查询Mock配置
     */
    @GetMapping("/list")
    @ApiOperation(value = "分页查询Mock配置")
    public ResultData<PageList<MockConfigDO>> getMockConfigList(
            @RequestParam(value = "nodeId", required = false) Integer nodeId,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        try {
            PageList<MockConfigDO> pageList = mockConfigService.findByPage(nodeId, pageNum, pageSize);
            return ResponseRenderUtils.render(pageList);
        } catch (Exception e) {
            log.error("查询Mock配置失败", e);
            return ResponseRenderUtils.error("查询Mock配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据节点ID查询所有Mock配置
     */
    @GetMapping("/node/{nodeId}")
    @ApiOperation(value = "根据节点ID查询所有Mock配置")
    public ResultData<List<MockConfigDO>> getMockConfigsByNodeId(@PathVariable Integer nodeId) {
        try {
            List<MockConfigDO> list = mockConfigService.findByNodeId(nodeId);
            return ResponseRenderUtils.render(list);
        } catch (Exception e) {
            log.error("查询Mock配置失败", e);
            return ResponseRenderUtils.error("查询Mock配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据MockID查询Mock配置详情
     */
    @GetMapping("/{mockId}")
    @ApiOperation(value = "查询Mock配置详情")
    public ResultData<MockConfigDO> getMockConfigById(@PathVariable Integer mockId) {
        try {
            MockConfigDO mockConfig = mockConfigService.findById(mockId);
            if (mockConfig == null) {
                return ResponseRenderUtils.error("Mock配置不存在");
            }
            return ResponseRenderUtils.render(mockConfig);
        } catch (Exception e) {
            log.error("查询Mock配置失败", e);
            return ResponseRenderUtils.error("查询Mock配置失败: " + e.getMessage());
        }
    }

    /**
     * 创建Mock配置
     */
    @PostMapping("/create")
    @ApiOperation(value = "创建Mock配置")
    public ResultData<MockConfigDO> createMockConfig(@RequestBody MockConfigDO mockConfig) {
        try {
            if (mockConfig.getNodeId() == null) {
                return ResponseRenderUtils.error("节点ID不能为空");
            }
            if (mockConfig.getCaseName() == null || mockConfig.getCaseName().trim().isEmpty()) {
                return ResponseRenderUtils.error("场景名称不能为空");
            }
            if (mockConfig.getMockResponse() == null || mockConfig.getMockResponse().trim().isEmpty()) {
                return ResponseRenderUtils.error("Mock响应数据不能为空");
            }

            MockConfigDO saved = mockConfigService.save(mockConfig);
            return ResponseRenderUtils.render(saved);
        } catch (Exception e) {
            log.error("创建Mock配置失败", e);
            return ResponseRenderUtils.error("创建Mock配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新Mock配置
     */
    @PostMapping("/update")
    @ApiOperation(value = "更新Mock配置")
    public ResultData<Integer> updateMockConfig(@RequestBody MockConfigDO mockConfig) {
        try {
            if (mockConfig.getMockId() == null) {
                return ResponseRenderUtils.error("MockID不能为空");
            }

            int result = mockConfigService.update(mockConfig);
            return ResponseRenderUtils.render(result);
        } catch (Exception e) {
            log.error("更新Mock配置失败", e);
            return ResponseRenderUtils.error("更新Mock配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除Mock配置
     */
    @DeleteMapping("/{mockId}")
    @ApiOperation(value = "删除Mock配置")
    public ResultData<String> deleteMockConfig(@PathVariable Integer mockId) {
        try {
            int result = mockConfigService.deleteById(mockId);
            if (result > 0) {
                return ResponseRenderUtils.render("Mock配置删除成功");
            } else {
                return ResponseRenderUtils.error("Mock配置不存在");
            }
        } catch (Exception e) {
            log.error("删除Mock配置失败", e);
            return ResponseRenderUtils.error("删除Mock配置失败: " + e.getMessage());
        }
    }

    /**
     * 根据节点ID删除所有Mock配置
     */
    @DeleteMapping("/node/{nodeId}")
    @ApiOperation(value = "根据节点ID删除所有Mock配置")
    public ResultData<String> deleteMockConfigsByNodeId(@PathVariable Integer nodeId) {
        try {
            int result = mockConfigService.deleteByNodeId(nodeId);
            return ResponseRenderUtils.render("删除了" + result + "条Mock配置");
        } catch (Exception e) {
            log.error("删除Mock配置失败", e);
            return ResponseRenderUtils.error("删除Mock配置失败: " + e.getMessage());
        }
    }

    /**
     * 调用Mock接口（实际调用）
     * 根据NodeId和MockId构建节点并执行
     */
    @PostMapping("/invoke")
    @ApiOperation(value = "调用Mock接口")
    public ResultData<Map<String, Object>> invokeMock(
            @RequestParam("nodeId") Integer nodeId,
            @RequestParam("mockId") Integer mockId,
            @RequestBody Map<String, Object> businessData) {
        try {
            // 使用buildWithMock方法构建带Mock配置的节点
            InterfaceWorkflowNode workflowNode = workFlowNodeBuilder.buildWithMock(nodeId, mockId);

            // 执行节点
            Map<String, Object> result = workflowNodeService.executeFlowNode(workflowNode, businessData);

            return ResponseRenderUtils.render(result);
        } catch (IllegalArgumentException e) {
            log.error("调用Mock接口参数错误, nodeId={}, mockId={}", nodeId, mockId, e);
            return ResponseRenderUtils.error(e.getMessage());
        } catch (Exception e) {
            log.error("调用Mock接口失败, nodeId={}, mockId={}", nodeId, mockId, e);
            return ResponseRenderUtils.error("调用Mock接口失败: " + e.getMessage());
        }
    }
}
