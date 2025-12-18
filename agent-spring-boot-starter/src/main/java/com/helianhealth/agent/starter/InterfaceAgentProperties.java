package com.helianhealth.agent.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Interface Agent 配置属性
 */
@ConfigurationProperties(prefix = "interface-agent")
public class InterfaceAgentProperties {

    /**
     * 是否启用 Interface Agent
     */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
