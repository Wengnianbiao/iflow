package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import lombok.Builder;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 启动工作流实例的引擎入参：按 {@code name}（+ 可选 {@code version}）与入参启动；
 * 定义由引擎按 name/version 查询（version 为 null 取最新）。
 */
@Getter
@Builder
public class StartWorkflowInput {

    private final String name;

    /** 版本；为 {@code null} 时取最新版本 */
    private final Integer version;

    @Builder.Default
    private final Map<String, Object> workflowInput = new HashMap<>();

    public static StartWorkflowInput from(StartWorkflowRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("工作流 name 不能为空");
        }
        return StartWorkflowInput.builder()
                .name(request.getName())
                .version(request.getVersion())
                .workflowInput(
                        request.getInput() != null
                                ? new HashMap<>(request.getInput())
                                : new HashMap<>())
                .build();
    }
}
