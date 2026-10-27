package com.helianhealth.iflow.console.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 控制台模块配置。
 */
@ConfigurationProperties(prefix = "agent.console")
public class ConsoleProperties {

    /**
     * 是否启用控制台（工作流定义 CRUD + UI）。
     */
    private boolean enabled = true;

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
