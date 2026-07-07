package com.helianhealth.agent.common.domain.metadata.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 可复用任务定义。
 *
 * <p>描述重试、超时、并发限制等执行策略，可被 {@link com.helianhealth.agent.common.domain.metadata.workflow.WorkflowTask}
 * 内联引用或独立注册。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDef {

    public enum TimeoutPolicy {
        RETRY,
        TIME_OUT_WF,
        ALERT_ONLY
    }

    public enum RetryLogic {
        FIXED,
        EXPONENTIAL_BACKOFF,
        LINEAR_BACKOFF
    }

    /**
     * 任务定义名（全局唯一）
     */
    private String name;

    private String description;

    @Builder.Default
    private int retryCount = 3;

    @Builder.Default
    private long timeoutSeconds = 3600;

    @Builder.Default
    private TimeoutPolicy timeoutPolicy = TimeoutPolicy.TIME_OUT_WF;

    @Builder.Default
    private RetryLogic retryLogic = RetryLogic.FIXED;

    @Builder.Default
    private int retryDelaySeconds = 60;

    @Builder.Default
    private long responseTimeoutSeconds = 3600;

    @Builder.Default
    private List<String> inputKeys = new ArrayList<>();

    @Builder.Default
    private List<String> outputKeys = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> inputTemplate = new HashMap<>();
}
