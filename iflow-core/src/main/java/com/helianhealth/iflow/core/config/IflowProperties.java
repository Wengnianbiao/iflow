package com.helianhealth.iflow.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * iFlow 配置属性
 */
@ConfigurationProperties(prefix = "iflow")
public class IflowProperties {

    /**
     * 是否启用 iFlow
     */
    private boolean enabled = true;

    private Duration lockTimeToTry = Duration.ofMillis(500);
    private Duration lockLeaseTime = Duration.ofSeconds(60);

    private final WorkflowExecution workflowExecution = new WorkflowExecution();

    public Duration getLockTimeToTry() {
        return lockTimeToTry;
    }

    public void setLockTimeToTry(Duration lockTimeToTry) {
        if (lockTimeToTry == null || lockTimeToTry.isNegative()) {
            throw new IllegalArgumentException("执行锁等待时间必须为非负时长");
        }
        this.lockTimeToTry = lockTimeToTry;
    }

    public Duration getLockLeaseTime() {
        return lockLeaseTime;
    }

    public void setLockLeaseTime(Duration lockLeaseTime) {
        if (lockLeaseTime == null || lockLeaseTime.toMillis() <= 0) {
            throw new IllegalArgumentException("执行锁租约必须至少为 1ms");
        }
        this.lockLeaseTime = lockLeaseTime;
    }

    public WorkflowExecution getWorkflowExecution() {
        return workflowExecution;
    }

    public static class WorkflowExecution {
        private long defaultTimeoutSeconds = 60;
        private final Sweeper sweeper = new Sweeper();

        public long getDefaultTimeoutSeconds() {
            return defaultTimeoutSeconds;
        }

        public void setDefaultTimeoutSeconds(long defaultTimeoutSeconds) {
            if (defaultTimeoutSeconds <= 0) {
                throw new IllegalArgumentException("工作流默认超时秒数必须大于 0");
            }
            this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        }

        public Sweeper getSweeper() {
            return sweeper;
        }
    }

    public static class Sweeper {
        private boolean enabled = true;
        private long intervalSeconds = 1;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getIntervalSeconds() {
            return intervalSeconds;
        }

        public void setIntervalSeconds(long intervalSeconds) {
            if (intervalSeconds <= 0 || intervalSeconds > Long.MAX_VALUE / 1000) {
                throw new IllegalArgumentException("工作流扫描间隔必须为有效的正整数秒");
            }
            this.intervalSeconds = intervalSeconds;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
