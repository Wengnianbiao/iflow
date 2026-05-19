package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.ai.convert.MockConfigMcpConverter;
import com.helianhealth.agent.ai.model.MockConfigMcpView;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.repository.model.domain.MockConfig;
import com.helianhealth.agent.repository.service.MockConfigCommandService;
import com.helianhealth.agent.repository.service.MockConfigQueryService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ops 接口 - Mock 配置管理（底层 REST 能力面）。
 *
 * <p>对外字段统一走 {@link MockConfigMcpView}，与其它 MCP View 对齐，
 * 避免把 {@link MockConfig} 数据库实体直接暴露给 AI。</p>
 */
@RestController
@RequestMapping("/v1/ai/ops/mock-config")
@Slf4j
@AllArgsConstructor
public class OpsMockConfigController {

    private final MockConfigQueryService mockConfigQueryService;
    private final MockConfigCommandService mockConfigCommandService;

    @GetMapping("/list/{nodeId}")
    public ResultData<List<MockConfigMcpView>> listByNodeId(@PathVariable Integer nodeId) {
        try {
            log.info("Ops 查询 Mock 配置列表: nodeId={}", nodeId);
            List<MockConfig> mockConfigs = mockConfigQueryService.findByNodeId(nodeId);
            return ResultData.ok(MockConfigMcpConverter.toMcpViewList(mockConfigs));
        } catch (Exception e) {
            log.error("Ops 查询 Mock 配置列表失败", e);
            return ResultData.error("查询 Mock 配置列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/get/{mockId}")
    public ResultData<MockConfigMcpView> getMock(@PathVariable Integer mockId) {
        try {
            log.info("Ops 查询 Mock 配置: mockId={}", mockId);
            MockConfig mockConfig = mockConfigQueryService.findById(mockId);
            if (mockConfig == null) {
                return ResultData.error("Mock 配置不存在");
            }
            return ResultData.ok(MockConfigMcpConverter.toMcpView(mockConfig));
        } catch (Exception e) {
            log.error("Ops 查询 Mock 配置失败", e);
            return ResultData.error("查询 Mock 配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public ResultData<Integer> createMock(@RequestBody MockConfigMcpView view) {
        try {
            log.info("Ops 创建 Mock 配置: {}", view);
            if (view == null || view.getNodeId() == null) {
                return ResultData.error("nodeId 不能为空");
            }
            MockConfig mockConfig = MockConfigMcpConverter.toDomain(view);
            mockConfig.setMockId(null);
            mockConfigCommandService.insert(mockConfig);
            return ResultData.ok(mockConfig.getMockId());
        } catch (Exception e) {
            log.error("Ops 创建 Mock 配置失败", e);
            return ResultData.error("创建 Mock 配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/update")
    public ResultData<Integer> updateMock(@RequestBody MockConfigMcpView view) {
        try {
            log.info("Ops 更新 Mock 配置: {}", view);
            if (view == null || view.getMockId() == null) {
                return ResultData.error("mockId 不能为空");
            }
            int result = mockConfigCommandService.update(MockConfigMcpConverter.toDomain(view));
            return ResultData.ok(result);
        } catch (Exception e) {
            log.error("Ops 更新 Mock 配置失败", e);
            return ResultData.error("更新 Mock 配置失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{mockId}")
    public ResultData<String> deleteMock(@PathVariable Integer mockId) {
        try {
            log.info("Ops 删除 Mock 配置: mockId={}", mockId);
            int result = mockConfigCommandService.deleteById(mockId);
            if (result > 0) {
                return ResultData.ok("Mock 配置删除成功");
            } else {
                return ResultData.error("Mock 配置不存在");
            }
        } catch (Exception e) {
            log.error("Ops 删除 Mock 配置失败", e);
            return ResultData.error("删除 Mock 配置失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete-by-node/{nodeId}")
    public ResultData<String> deleteByNodeId(@PathVariable Integer nodeId) {
        try {
            log.info("Ops 批量删除 Mock 配置: nodeId={}", nodeId);
            int result = mockConfigCommandService.deleteByNodeId(nodeId);
            return ResultData.ok("已删除 " + result + " 个 Mock 配置");
        } catch (Exception e) {
            log.error("Ops 批量删除 Mock 配置失败", e);
            return ResultData.error("批量删除 Mock 配置失败: " + e.getMessage());
        }
    }
}
