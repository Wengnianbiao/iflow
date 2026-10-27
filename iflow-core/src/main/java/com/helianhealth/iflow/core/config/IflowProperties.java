package com.helianhealth.iflow.core.config;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * iFlow 配置属性
 */
@ConfigurationProperties(prefix = "iflow")
public class IflowProperties {

    /**
     * 是否启用 iFlow
     */
    private boolean enabled = true;

    private final WorkflowExecution workflowExecution = new WorkflowExecution();
    private final TaskExecution taskExecution = new TaskExecution();

    public TaskExecution getTaskExecution() {
        return taskExecution;
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

    /** 节点未配置时使用的任务策略；在启动定义解析时写入实例快照。 */
    public static class TaskExecution {
        private int defaultRetryCount = 0;
        private int defaultRetryDelaySeconds = 0;
        private int defaultStartDelay = 0;
        private WorkflowTask.RetryLogic defaultRetryLogic = WorkflowTask.RetryLogic.FIXED;

        public int getDefaultRetryCount() {
            return defaultRetryCount;
        }

        public void setDefaultRetryCount(int defaultRetryCount) {
            if (defaultRetryCount < 0) {
                throw new IllegalArgumentException("默认重试次数必须是非负整数");
            }
            this.defaultRetryCount = defaultRetryCount;
        }

        public int getDefaultRetryDelaySeconds() {
            return defaultRetryDelaySeconds;
        }

        public void setDefaultRetryDelaySeconds(int defaultRetryDelaySeconds) {
            if (defaultRetryDelaySeconds < 0) {
                throw new IllegalArgumentException("默认重试间隔必须是非负整数");
            }
            this.defaultRetryDelaySeconds = defaultRetryDelaySeconds;
        }

        public int getDefaultStartDelay() {
            return defaultStartDelay;
        }

        public void setDefaultStartDelay(int defaultStartDelay) {
            if (defaultStartDelay < 0) {
                throw new IllegalArgumentException("默认启动延迟必须是非负整数");
            }
            this.defaultStartDelay = defaultStartDelay;
        }

        public WorkflowTask.RetryLogic getDefaultRetryLogic() {
            return defaultRetryLogic;
        }

        public void setDefaultRetryLogic(WorkflowTask.RetryLogic defaultRetryLogic) {
            if (defaultRetryLogic == null) {
                throw new IllegalArgumentException("默认重试方式不能为空");
            }
            this.defaultRetryLogic = defaultRetryLogic;
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
