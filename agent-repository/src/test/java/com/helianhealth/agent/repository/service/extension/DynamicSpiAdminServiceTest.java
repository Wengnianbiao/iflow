package com.helianhealth.agent.repository.service.extension;

import com.helianhealth.agent.common.enums.ExtensionType;
import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.repository.model.convert.NodeExtensionConfigConvert;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.repository.model.dto.NodeExtensionConfigCreateReq;
import com.helianhealth.agent.repository.model.dto.NodeExtensionConfigUpdateReq;
import com.helianhealth.agent.repository.service.NodeExtensionConfigCommandService;
import com.helianhealth.agent.repository.service.NodeExtensionConfigQueryService;
import com.helianhealth.agent.repository.service.extension.impl.DynamicSpiAdminServiceImpl;
import com.helianhealth.agent.repository.service.extension.strategy.ExtensionTypeStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DynamicSpiAdminService 单元测试。
 * <p>验证主干流程：本表 CRUD + SPI 运行时注册/卸载 + 策略回调顺序。
 * <p>SUT 通过接口 {@link DynamicSpiAdminService} 持有；构造时实例化默认实现 {@link DynamicSpiAdminServiceImpl}。
 */
@ExtendWith(MockitoExtension.class)
class DynamicSpiAdminServiceTest {

    @Mock
    private NodeExtensionConfigQueryService extensionConfigQueryService;
    @Mock
    private NodeExtensionConfigCommandService extensionConfigCommandService;
    @Mock
    private NodeExtensionConfigConvert extensionConfigConvert;
    @Mock
    private ExtensionTypeStrategy filterStrategy;
    @Mock
    private ExtensionTypeStrategy remoteStrategy;
    @Mock
    private AgentExtensionLoader<Object> mockLoader;

    private DynamicSpiAdminService service;

    private static final String SPI_INTERFACE = "com.helianhealth.agent.invocation.remote.filter.AgentFilter";
    private static final String EXTENSION_NAME = "test-filter";
    private static final String CLASS_NAME = "com.example.TestFilter";
    private static final List<Integer> NODE_IDS = Collections.singletonList(1);

    @BeforeEach
    void setUp() {
        when(filterStrategy.supportType()).thenReturn(ExtensionType.FILTER);
        when(remoteStrategy.supportType()).thenReturn(ExtensionType.REMOTE);
        service = new DynamicSpiAdminServiceImpl(
                extensionConfigQueryService,
                extensionConfigCommandService,
                extensionConfigConvert,
                Arrays.asList(filterStrategy, remoteStrategy)
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
        @DisplayName("启用状态：策略 onRegister + DB插入 + SPI注册")
        void registerExtension_enabled_fullFlow() throws Exception {
            NodeExtensionConfigCreateReq req = buildCreateReq();
            NodeExtensionConfig config = buildConfig(1);
            when(extensionConfigConvert.fromCreateReq(req)).thenReturn(config);

            try (MockedStatic<AgentExtensionLoader> loaderStatic = mockStatic(AgentExtensionLoader.class)) {
                loaderStatic.when(() -> AgentExtensionLoader.getExtensionLoader(anyString()))
                        .thenReturn(mockLoader);

                service.registerExtension(ExtensionType.FILTER, req);

                verify(filterStrategy).onRegister(eq(config));
                verify(extensionConfigCommandService).insert(config);
                verify(mockLoader).registerDynamicExtension(EXTENSION_NAME, CLASS_NAME, config.getSourceCode(), true);
            }
        }

        @Test
        @DisplayName("禁用状态：策略 onRegister + DB插入，不注册SPI")
        void registerExtension_disabled_skipSpi() {
            NodeExtensionConfigCreateReq req = buildCreateReq();
            req.setEnabled(0);
            NodeExtensionConfig config = buildConfig(0);
            when(extensionConfigConvert.fromCreateReq(req)).thenReturn(config);

            service.registerExtension(ExtensionType.FILTER, req);

            verify(filterStrategy).onRegister(eq(config));
            verify(extensionConfigCommandService).insert(config);
            verifyNoInteractions(mockLoader);
        }

        @Test
        @DisplayName("策略 onRegister 抛异常：DB和SPI都不执行")
        void registerExtension_strategyFails_nothingElseExecuted() {
            NodeExtensionConfigCreateReq req = buildCreateReq();
            NodeExtensionConfig config = buildConfig(1);
            when(extensionConfigConvert.fromCreateReq(req)).thenReturn(config);
            doThrow(new RuntimeException("strategy failed"))
                    .when(filterStrategy).onRegister(any());

            assertThrows(RuntimeException.class,
                    () -> service.registerExtension(ExtensionType.FILTER, req));

            verify(extensionConfigCommandService, never()).insert(any());
        }

        @Test
        @DisplayName("SPI注册失败：抛出AgentException")
        void registerExtension_spiRegisterFails_throwsAgentException() throws Exception {
            NodeExtensionConfigCreateReq req = buildCreateReq();
            NodeExtensionConfig config = buildConfig(1);
            when(extensionConfigConvert.fromCreateReq(req)).thenReturn(config);

            try (MockedStatic<AgentExtensionLoader> loaderStatic = mockStatic(AgentExtensionLoader.class)) {
                loaderStatic.when(() -> AgentExtensionLoader.getExtensionLoader(anyString()))
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
        @DisplayName("正常更新：策略 onUpdate + DB更新 + 卸载旧SPI + 注册新SPI")
        void updateExtension_fullFlow() throws Exception {
            NodeExtensionConfigUpdateReq req = buildUpdateReq();
            NodeExtensionConfig newConfig = buildConfig(1);
            NodeExtensionConfig oldConfig = buildConfig(1);
            oldConfig.setExtensionName("old-filter");

            when(extensionConfigConvert.fromUpdateReq(req)).thenReturn(newConfig);
            when(extensionConfigQueryService.selectById(1)).thenReturn(oldConfig);

            try (MockedStatic<AgentExtensionLoader> loaderStatic = mockStatic(AgentExtensionLoader.class)) {
                loaderStatic.when(() -> AgentExtensionLoader.getExtensionLoader(anyString()))
                        .thenReturn(mockLoader);

                service.updateExtension(ExtensionType.FILTER, req);

                verify(filterStrategy).onUpdate(eq(newConfig), eq(oldConfig));
                verify(extensionConfigCommandService).update(newConfig);
                verify(mockLoader).removeDynamicExtension("old-filter");
                verify(mockLoader).registerDynamicExtension(EXTENSION_NAME, CLASS_NAME, newConfig.getSourceCode(), true);
            }
        }

        @Test
        @DisplayName("旧配置不存在：跳过卸载，正常注册")
        void updateExtension_noOldConfig_skipUnregister() throws Exception {
            NodeExtensionConfigUpdateReq req = buildUpdateReq();
            NodeExtensionConfig newConfig = buildConfig(1);

            when(extensionConfigConvert.fromUpdateReq(req)).thenReturn(newConfig);
            when(extensionConfigQueryService.selectById(1)).thenReturn(null);

            try (MockedStatic<AgentExtensionLoader> loaderStatic = mockStatic(AgentExtensionLoader.class)) {
                loaderStatic.when(() -> AgentExtensionLoader.getExtensionLoader(anyString()))
                        .thenReturn(mockLoader);

                service.updateExtension(ExtensionType.FILTER, req);

                verify(filterStrategy).onUpdate(eq(newConfig), eq(null));
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
        @DisplayName("正常禁用：策略 onUnregister + DB更新 + SPI卸载")
        void disableAndUnregister_success() {
            NodeExtensionConfig config = buildConfig(1);
            when(extensionConfigQueryService.selectById(1)).thenReturn(config);

            try (MockedStatic<AgentExtensionLoader> loaderStatic = mockStatic(AgentExtensionLoader.class)) {
                loaderStatic.when(() -> AgentExtensionLoader.getExtensionLoader(anyString()))
                        .thenReturn(mockLoader);

                service.disableAndUnregister(1);

                verify(filterStrategy).onUnregister(config);
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
            verify(filterStrategy, never()).onUnregister(any());
            verify(remoteStrategy, never()).onUnregister(any());
        }
    }
}
