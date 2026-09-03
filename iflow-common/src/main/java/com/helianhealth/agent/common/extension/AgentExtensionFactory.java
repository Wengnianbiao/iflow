package com.helianhealth.agent.common.extension;

/**
 * Extension Factory
 * 扩展工厂，用于获取不同来源的依赖对象
 */
@AgentSPI
public interface AgentExtensionFactory {

    /**
     * 获取扩展对象
     *
     * @param type 对象类型
     * @param name 对象名称
     * @return 对象实例，如果不存在则返回null
     */
    <T> T getExtension(Class<T> type, String name);
}
