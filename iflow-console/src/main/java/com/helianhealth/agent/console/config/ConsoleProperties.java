package com.helianhealth.agent.console.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 控制台模块配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "agent.console")
public class ConsoleProperties {

    /**
     * 是否启用控制台（工作流定义 CRUD + UI）。
     */
    private boolean enabled = true;
}
