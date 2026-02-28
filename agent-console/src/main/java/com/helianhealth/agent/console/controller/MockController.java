package com.helianhealth.agent.console.controller;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import com.helianhealth.agent.console.model.dto.MockInvokeRequest;
import com.helianhealth.agent.console.service.MockScheduleService;
import com.helianhealth.agent.repository.model.domain.MockConfig;
import com.helianhealth.agent.repository.model.po.MockConfigDO;
import com.helianhealth.agent.repository.service.MockConfigCommandService;
import com.helianhealth.agent.repository.service.MockConfigQueryService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Mock配置管理控制器
 * 提供Mock配置CRUD及Mock调用接口
 */
@RestController
@RequestMapping("/v1/console/mock")
@Slf4j
@AllArgsConstructor
public class MockController {

    private final MockConfigQueryService mockConfigQueryService;
    private final MockConfigCommandService mockConfigCommandService;
    private final MockScheduleService mockScheduleService;

    @GetMapping("/list")
    public ResultData<PageList<MockConfig>> getMockConfigList(
            @RequestParam(value = "nodeId", required = false) Integer nodeId,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        try {
            PageList<MockConfig> pageList = mockConfigQueryService.findByPage(nodeId, pageNum, pageSize);
            return ResponseRenderUtils.render(pageList);
        } catch (Exception e) {
            log.error("查询Mock配置失败", e);
            return ResponseRenderUtils.error("查询Mock配置失败: " + e.getMessage());
        }
    }

    @GetMapping("/node/{nodeId}")
    public ResultData<List<MockConfig>> getMockConfigsByNodeId(@PathVariable Integer nodeId) {
        try {
            List<MockConfig> list = mockConfigQueryService.findByNodeId(nodeId);
            return ResponseRenderUtils.render(list);
        } catch (Exception e) {
            log.error("查询Mock配置失败", e);
            return ResponseRenderUtils.error("查询Mock配置失败: " + e.getMessage());
        }
    }

    @GetMapping("/{mockId}")
    public ResultData<MockConfig> getMockConfigById(@PathVariable Integer mockId) {
        try {
            MockConfig mockConfig = mockConfigQueryService.findById(mockId);
            if (mockConfig == null) {
                return ResponseRenderUtils.error("Mock配置不存在");
            }
            return ResponseRenderUtils.render(mockConfig);
        } catch (Exception e) {
            log.error("查询Mock配置失败", e);
            return ResponseRenderUtils.error("查询Mock配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/create")
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

            mockConfigCommandService.insert(mockConfig);
            return ResponseRenderUtils.render(mockConfig);
        } catch (Exception e) {
            log.error("创建Mock配置失败", e);
            return ResponseRenderUtils.error("创建Mock配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/update")
    public ResultData<Integer> updateMockConfig(@RequestBody MockConfigDO mockConfig) {
        try {
            if (mockConfig.getMockId() == null) {
                return ResponseRenderUtils.error("MockID不能为空");
            }

            int result = mockConfigCommandService.update(mockConfig);
            return ResponseRenderUtils.render(result);
        } catch (Exception e) {
            log.error("更新Mock配置失败", e);
            return ResponseRenderUtils.error("更新Mock配置失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{mockId}")
    public ResultData<String> deleteMockConfig(@PathVariable Integer mockId) {
        try {
            int result = mockConfigCommandService.deleteById(mockId);
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

    @DeleteMapping("/node/{nodeId}")
    public ResultData<String> deleteMockConfigsByNodeId(@PathVariable Integer nodeId) {
        try {
            int result = mockConfigCommandService.deleteByNodeId(nodeId);
            return ResponseRenderUtils.render("删除了" + result + "条Mock配置");
        } catch (Exception e) {
            log.error("删除Mock配置失败", e);
            return ResponseRenderUtils.error("删除Mock配置失败: " + e.getMessage());
        }
    }

    /**
     * 执行Mock调度
     * 支持多节点调度链路，每个节点配置对应的MockId
     */
    @PostMapping("/invoke")
    public ResultData<Map<String, Object>> invokeMockNode(@RequestBody MockInvokeRequest request) {
        try {
            if (request.getMockConfigs() == null || request.getMockConfigs().isEmpty()) {
                return ResponseRenderUtils.error("Mock配置不能为空");
            }
            Map<String, Object> result = mockScheduleService.mockSchedule(request);
            return ResponseRenderUtils.render(result);
        } catch (IllegalArgumentException e) {
            log.error("调用Mock节点参数错误", e);
            return ResponseRenderUtils.error(e.getMessage());
        } catch (Exception e) {
            log.error("调用Mock节点失败", e);
            return ResponseRenderUtils.error("调用Mock节点失败: " + e.getMessage());
        }
    }
}
