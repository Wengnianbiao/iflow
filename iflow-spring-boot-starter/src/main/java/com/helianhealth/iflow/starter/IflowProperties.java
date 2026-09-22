package com.helianhealth.iflow.starter;

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
