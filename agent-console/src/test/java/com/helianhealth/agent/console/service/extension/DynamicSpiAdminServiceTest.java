package com.helianhealth.agent.console.service.extension;

import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.console.controller.request.NodeExtensionConfigCreateReq;
import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.console.model.convert.NodeExtensionConfigConvert;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigUpdateReq;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.repository.service.NodeExtensionConfigCommandService;
import com.helianhealth.agent.repository.service.NodeExtensionConfigQueryService;
import com.helianhealth.agent.repository.service.WorkflowNodeQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DynamicSpiAdminService 单元测试
 *
 * 测试策略：
 * - 纯单元测试，不依赖 Spring 容器
 * - Mock 所有外部依赖（DB服务、Convert、AgentExtensionLoader）
 * - 验证调用顺序、异常传播、分支逻辑
 */
@ExtendWith(MockitoExtension.class)
class DynamicSpiAdminServiceTest {

    @Mock
    private NodeExtensionConfigQueryService extensionConfigQueryService;
    @Mock
    private NodeExtensionConfigCommandService extensionConfigCommandService;
    @Mock
    private NodeExtensionConfigService extensionNodeUpdateService;
    @Mock
    private WorkflowNodeQueryService workflowNodeQueryService;
    @Mock
    private NodeExtensionConfigConvert extensionConfigConvert;
    @Mock
    private WorkflowExtensionBindingService workflowExtensionBindingService;
    @Mock
    private AgentExtensionLoader<Object> mockLoader;

    private DynamicSpiAdminService service;

    private static final String SPI_INTERFACE = "com.helianhealth.agent.invocation.remote.filter.AgentFilter";
    private static final String EXTENSION_NAME = "test-filter";
    private static final String CLASS_NAME = "com.example.TestFilter";
    private static final List<Integer> NODE_IDS = Collections.singletonList(1);

    @BeforeEach
    void setUp() {
        service = new DynamicSpiAdminService(
                extensionConfigQueryService,
                extensionConfigCommandService,
                extensionNodeUpdateService,
                workflowNodeQueryService,
                extensionConfigConvert,
                workflowExtensionBindingService
        );
    }

    private NodeExtensionConfig buildConfig(Integer enabled) {
        return NodeExtensionConfig.builder()
                .configId(1)
                .bindingIds(NODE_IDS)
                .spiInterface(SPI_INTERFACE)
                .extensionName(EXTENSION_NAME)
                .className(CLASS_NAME)
                .sourceCode("public class TestFilter {}")
                .extensionMode("ONLINE")
                .enabled(enabled)
                .build();
    }

    private NodeExtensionConfigCreateReq buildCreateReq() {
        NodeExtensionConfigCreateReq req = new NodeExtensionConfigCreateReq();
        req.setBindingIds(NODE_IDS);
        req.setSpiInterface(SPI_INTERFACE);
        req.setExtensionName(EXTENSION_NAME);
        req.setClassName(CLASS_NAME);
        req.setSourceCode("public class TestFilter {}");
        req.setExtensionMode("ONLINE");
        req.setEnabled(1);
        return req;
    }

    private NodeExtensionConfigUpdateReq buildUpdateReq() {
        NodeExtensionConfigUpdateReq req = new NodeExtensionConfigUpdateReq();
        req.setConfigId(1);
        req.setBindingIds(NODE_IDS);
        req.setSpiInterface(SPI_INTERFACE);
        req.setExtensionName(EXTENSION_NAME);
        req.setClassName(CLASS_NAME);
        req.setSourceCode("public class TestFilter {}");
        req.setExtensionMode("ONLINE");
        req.setEnabled(1);
        return req;
    }

    // ==================== registerExtension ====================

    @Nested
    @DisplayName("registerExtension")
    class RegisterExtensionTests {

        @Test
        @DisplayName("启用状态：节点更新 + DB插入 + SPI注册")
        void registerExtension_enabled_fullFlow() throws Exception {
            NodeExtensionConfigCreateReq req = buildCreateReq();
            NodeExtensionConfig config = buildConfig(1);
            when(extensionConfigConvert.fromCreateReq(req)).thenReturn(config);

            try (MockedStatic<AgentExtensionLoader> loaderStatic = mockStatic(AgentExtensionLoader.class)) {
                loaderStatic.when(() -> AgentExtensionLoader.getExtensionLoader(any()))
                        .thenReturn(mockLoader);

                service.registerExtension(ExtensionType.FILTER, req);

                // 验证三步都执行了
                verify(extensionNodeUpdateService).updateNodeExtensionField(
                        eq(ExtensionType.FILTER), eq(EXTENSION_NAME), eq(NODE_IDS), any());
                verify(extensionConfigCommandService).insert(config);
                verify(mockLoader).registerDynamicExtension(EXTENSION_NAME, CLASS_NAME, config.getSourceCode(), true);
            }
        }

        @Test
        @DisplayName("禁用状态：节点更新 + DB插入，不注册SPI")
        void registerExtension_disabled_skipSpi() {
            NodeExtensionConfigCreateReq req = buildCreateReq();
            req.setEnabled(0);
            NodeExtensionConfig config = buildConfig(0);
            when(extensionConfigConvert.fromCreateReq(req)).thenReturn(config);

            service.registerExtension(ExtensionType.FILTER, req);

            verify(extensionNodeUpdateService).updateNodeExtensionField(
                    eq(ExtensionType.FILTER), eq(EXTENSION_NAME), eq(NODE_IDS), any());
            verify(extensionConfigCommandService).insert(config);
            // SPI不应该被调用
            verifyNoInteractions(mockLoader);
        }

        @Test
        @DisplayName("节点更新失败：异常传播，DB和SPI都不执行")
        void registerExtension_nodeUpdateFails_nothingElseExecuted() {
            NodeExtensionConfigCreateReq req = buildCreateReq();
            NodeExtensionConfig config = buildConfig(1);
            when(extensionConfigConvert.fromCreateReq(req)).thenReturn(config);
            doThrow(new RuntimeException("Node update failed"))
                    .when(extensionNodeUpdateService).updateNodeExtensionField(any(), any(), any(), any());

            assertThrows(RuntimeException.class,
                    () -> service.registerExtension(ExtensionType.FILTER, req));

            verify(extensionConfigCommandService, never()).insert(any());
        }

        @Test
        @DisplayName("DB插入失败：异常传播，SPI不执行")
        void registerExtension_dbInsertFails_spiNotExecuted() {
            NodeExtensionConfigCreateReq req = buildCreateReq();
            NodeExtensionConfig config = buildConfig(1);
            when(extensionConfigConvert.fromCreateReq(req)).thenReturn(config);
            doThrow(new RuntimeException("DB insert failed"))
                    .when(extensionConfigCommandService).insert(any());

            assertThrows(RuntimeException.class,
                    () -> service.registerExtension(ExtensionType.FILTER, req));
        }

        @Test
        @DisplayName("SPI注册失败：抛出AgentException")
        void registerExtension_spiRegisterFails_throwsAgentException() throws Exception {
            NodeExtensionConfigCreateReq req = buildCreateReq();
            NodeExtensionConfig config = buildConfig(1);
            when(extensionConfigConvert.fromCreateReq(req)).thenReturn(config);

            try (MockedStatic<AgentExtensionLoader> loaderStatic = mockStatic(AgentExtensionLoader.class)) {
                loaderStatic.when(() -> AgentExtensionLoader.getExtensionLoader(any()))
                        .thenReturn(mockLoader);
                doThrow(new Exception("Compile error"))
                        .when(mockLoader).registerDynamicExtension(any(), any(), any(), anyBoolean());

                assertThrows(AgentException.class,
                        () -> service.registerExtension(ExtensionType.FILTER, req));
            }
        }
    }

    // ==================== updateExtension ====================

    @Nested
    @DisplayName("updateExtension")
    class UpdateExtensionTests {

        @Test
        @DisplayName("正常更新：清理旧节点 + 更新新节点 + DB更新 + SPI刷新")
        void updateExtension_fullFlow() throws Exception {
            NodeExtensionConfigUpdateReq req = buildUpdateReq();
            NodeExtensionConfig newConfig = buildConfig(1);
            NodeExtensionConfig oldConfig = buildConfig(1);
            oldConfig.setExtensionName("old-filter");

            when(extensionConfigConvert.fromUpdateReq(req)).thenReturn(newConfig);
            when(extensionConfigQueryService.selectById(1)).thenReturn(oldConfig);

            try (MockedStatic<AgentExtensionLoader> loaderStatic = mockStatic(AgentExtensionLoader.class)) {
                loaderStatic.when(() -> AgentExtensionLoader.getExtensionLoader(any()))
                        .thenReturn(mockLoader);

                service.updateExtension(ExtensionType.FILTER, req);

                // 验证清理旧节点
                verify(extensionNodeUpdateService).clearNodeFields(
                        ExtensionType.FILTER, "old-filter", NODE_IDS);
                // 验证更新新节点
                verify(extensionNodeUpdateService).updateNodeExtensionField(
                        eq(ExtensionType.FILTER), eq(EXTENSION_NAME), eq(NODE_IDS), any());
                // 验证DB更新
                verify(extensionConfigCommandService).update(newConfig);
                // 验证卸载旧SPI + 注册新SPI
                verify(mockLoader).removeDynamicExtension("old-filter");
                verify(mockLoader).registerDynamicExtension(EXTENSION_NAME, CLASS_NAME, newConfig.getSourceCode(), true);
            }
        }

        @Test
        @DisplayName("旧配置不存在：跳过清理，正常注册")
        void updateExtension_noOldConfig_skipCleanup() throws Exception {
            NodeExtensionConfigUpdateReq req = buildUpdateReq();
            NodeExtensionConfig newConfig = buildConfig(1);

            when(extensionConfigConvert.fromUpdateReq(req)).thenReturn(newConfig);
            when(extensionConfigQueryService.selectById(1)).thenReturn(null);

            try (MockedStatic<AgentExtensionLoader> loaderStatic = mockStatic(AgentExtensionLoader.class)) {
                loaderStatic.when(() -> AgentExtensionLoader.getExtensionLoader(any()))
                        .thenReturn(mockLoader);

                service.updateExtension(ExtensionType.FILTER, req);

                verify(extensionNodeUpdateService, never()).clearNodeFields(any(), any(), any());
                verify(mockLoader, never()).removeDynamicExtension(any());
                verify(mockLoader).registerDynamicExtension(EXTENSION_NAME, CLASS_NAME, newConfig.getSourceCode(), true);
            }
        }
    }

    // ==================== disableAndUnregister ====================

    @Nested
    @DisplayName("disableAndUnregister")
    class DisableAndUnregisterTests {

        @Test
        @DisplayName("正常禁用：DB更新 + SPI卸载")
        void disableAndUnregister_success() {
            NodeExtensionConfig config = buildConfig(1);
            when(extensionConfigQueryService.selectById(1)).thenReturn(config);

            try (MockedStatic<AgentExtensionLoader> loaderStatic = mockStatic(AgentExtensionLoader.class)) {
                loaderStatic.when(() -> AgentExtensionLoader.getExtensionLoader(any()))
                        .thenReturn(mockLoader);

                service.disableAndUnregister(1);

                assertEquals(0, config.getEnabled());
                verify(extensionConfigCommandService).update(config);
                verify(mockLoader).removeDynamicExtension(EXTENSION_NAME);
            }
        }

        @Test
        @DisplayName("配置不存在：静默返回")
        void disableAndUnregister_notFound_silent() {
            when(extensionConfigQueryService.selectById(999)).thenReturn(null);

            service.disableAndUnregister(999);

            verify(extensionConfigCommandService, never()).update(any());
        }
    }

    // ==================== refresh ====================

}
