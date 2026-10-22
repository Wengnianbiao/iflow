package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import lombok.Builder;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 启动工作流实例的引擎入参：按 {@code route}（+ 可选 {@code version}）与入参启动；
 * 定义由引擎按 route/version 查询（version 为 null 取最新）。
 */
@Getter
@Builder(toBuilder = true)
public class StartWorkflowInput {

    /** 可选实例 ID；为 null 时由引擎生成。 */
    private final String workflowId;

    private final String route;

    /** 版本；为 {@code null} 时取最新版本 */
    private final Integer version;

    @Builder.Default
    private final Map<String, Object> workflowInput = new HashMap<>();

    public static StartWorkflowInput from(StartWorkflowRequest request) {
        if (request == null || !StringUtils.hasText(request.getRoute())) {
            throw new IllegalArgumentException("工作流 route 不能为空");
        }
        return StartWorkflowInput.builder()
                .route(request.getRoute())
                .version(request.getVersion())
                .workflowInput(
                        request.getInput() != null
                                ? new HashMap<>(request.getInput())
                                : new HashMap<>())
                .build();
    }
}
