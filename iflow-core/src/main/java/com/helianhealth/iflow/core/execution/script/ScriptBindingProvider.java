package com.helianhealth.iflow.core.execution.script;

import java.util.Map;

/**
 * 提供脚本中可按名称访问的绑定。Spring Bean 在引擎启动时读取，
 * 调用时传入的 provider 只为本次求值提供绑定。
 */
@FunctionalInterface
public interface ScriptBindingProvider {

    Map<String, Object> bindings();
}
