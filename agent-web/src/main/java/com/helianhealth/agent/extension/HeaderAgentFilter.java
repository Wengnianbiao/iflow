package com.helianhealth.agent.extension;

import com.helianhealth.agent.common.extension.AgentActivate;
import com.helianhealth.agent.invocation.remote.filter.AbstractHeaderAgentFilter;

import java.util.Map;

/**
 * 使用场景:
 * 对于厂商接口需要在请求头中添加动态的头信息(通常为token信息)
 * 通常接口是多个，可以通过该扩展点统一处理绑定过滤器SPI
 * 框架会通过节点的extensionParam字段+SPI机制再节点调用前封装请求头配置逻辑
 */
@AgentActivate(order = 50)
public class HeaderAgentFilter extends AbstractHeaderAgentFilter {

    @Override
    protected void doFilter(Map<String, String> headers) {
        // 在原始请求头中新增Authorization头
        headers.put("Authorization", getToken());
    }

    private String getToken() {
        // do something to get tokens

        return "Bearer " + "1234567890";
    }
}
