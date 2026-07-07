package com.helianhealth.agent.common.domain.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 启动工作流请求。
 *
 * <p>API / Console / Gateway 入参：通过 {@code name + version} 定位定义（{@code name} 即 {@link
 * com.helianhealth.agent.common.domain.metadata.workflow.WorkflowDef#getName()}；{@code version}
 * 为 {@code null} 时取最新版本），{@code input} 写入运行时工作流实例。定义解析由
 * {@code WorkflowExecutionService} 完成，调用方无需自行查询 {@code WorkflowDef}。
 *
 * <p>业务关联 ID（{@code correlationId}）不由调用方传入，启动时由定义上的 {@code correlationExpression}
 * 表达式解析生成。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartWorkflowRequest {

    private String name;

    /**
     * 版本；为 {@code null} 时取最新版本
     * */
    private Integer version;

    @Builder.Default
    private Map<String, Object> input = new HashMap<>();
}
