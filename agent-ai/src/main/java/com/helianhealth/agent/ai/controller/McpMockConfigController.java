package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import com.helianhealth.agent.mock.model.MockConfigDO;
import com.helianhealth.agent.mock.service.MockConfigService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP接口 - Mock配置管理
 * 提供给AI使用的Mock配置CRUD接口
 */
@RestController
@RequestMapping("/v1/ai/mcp/mock-config")
@Slf4j
@AllArgsConstructor
public class McpMockConfigController {

    private final MockConfigService mockConfigService;

    /**
     * 根据节点ID查询所有Mock配置
     * 用于AI获取某个节点下的所有Mock场景
     */
    @GetMapping("/list/{nodeId}")
    public ResultData<List<MockConfigDO>> listByNodeId(@PathVariable Integer nodeId) {
        try {
            log.info("MCP查询Mock配置列表: nodeId={}", nodeId);
            List<MockConfigDO> mockConfigs = mockConfigService.findByNodeId(nodeId);
            return ResponseRenderUtils.render(mockConfigs);
        } catch (Exception e) {
            log.error("MCP查询Mock配置列表失败", e);
            return ResponseRenderUtils.error("查询Mock配置列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据mockId查询单个Mock配置
     */
    @GetMapping("/get/{mockId}")
    public ResultData<MockConfigDO> getMock(@PathVariable Integer mockId) {
        try {
            log.info("MCP查询Mock配置: mockId={}", mockId);
            MockConfigDO mockConfig = mockConfigService.findById(mockId);
            if (mockConfig == null) {
                return ResponseRenderUtils.error("Mock配置不存在");
            }
            return ResponseRenderUtils.render(mockConfig);
        } catch (Exception e) {
            log.error("MCP查询Mock配置失败", e);
            return ResponseRenderUtils.error("查询Mock配置失败: " + e.getMessage());
        }
    }

    /**
     * 创建Mock配置
     */
    @PostMapping("/create")
    public ResultData<Integer> createMock(@RequestBody MockConfigDO mockConfig) {
        try {
            log.info("MCP创建Mock配置: {}", mockConfig);
            MockConfigDO savedMock = mockConfigService.save(mockConfig);
            return ResponseRenderUtils.render(savedMock.getMockId());
        } catch (Exception e) {
            log.error("MCP创建Mock配置失败", e);
            return ResponseRenderUtils.error("创建Mock配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新Mock配置
     */
    @PostMapping("/update")
    public ResultData<Integer> updateMock(@RequestBody MockConfigDO mockConfig) {
        try {
            log.info("MCP更新Mock配置: {}", mockConfig);
            if (mockConfig.getMockId() == null) {
                return ResponseRenderUtils.error("mockId不能为空");
            }
            int result = mockConfigService.update(mockConfig);
            return ResponseRenderUtils.render(result);
        } catch (Exception e) {
            log.error("MCP更新Mock配置失败", e);
            return ResponseRenderUtils.error("更新Mock配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除Mock配置
     */
    @DeleteMapping("/delete/{mockId}")
    public ResultData<String> deleteMock(@PathVariable Integer mockId) {
        try {
            log.info("MCP删除Mock配置: mockId={}", mockId);
            int result = mockConfigService.deleteById(mockId);
            if (result > 0) {
                return ResponseRenderUtils.render("Mock配置删除成功");
            } else {
                return ResponseRenderUtils.error("Mock配置不存在");
            }
        } catch (Exception e) {
            log.error("MCP删除Mock配置失败", e);
            return ResponseRenderUtils.error("删除Mock配置失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除某个节点下的所有Mock配置
     */
    @DeleteMapping("/delete-by-node/{nodeId}")
    public ResultData<String> deleteByNodeId(@PathVariable Integer nodeId) {
        try {
            log.info("MCP批量删除Mock配置: nodeId={}", nodeId);
            int result = mockConfigService.deleteByNodeId(nodeId);
            return ResponseRenderUtils.render("已删除" + result + "个Mock配置");
        } catch (Exception e) {
            log.error("MCP批量删除Mock配置失败", e);
            return ResponseRenderUtils.error("批量删除Mock配置失败: " + e.getMessage());
        }
    }
}
