package com.helianhealth.agent.invocation.aspect;

import com.helianhealth.agent.invocation.context.InvocationLogContextHolder;
import com.helianhealth.agent.invocation.context.LogContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 参数解析过程的日志收集切面
 * 用于收集参数预处理结果，供日志记录使用
 */
@Aspect
@Component
@Slf4j
public class ParamResolverLogAspect {

    /**
     * 收集参数预处理结果
     * 在 ParamResolver.resolveParamNodes() 方法执行后，将结果保存到日志上下文
     */
    @AfterReturning(
            pointcut = "execution(* com.helianhealth.agent.invocation.remote.ParamResolver.resolveParamNodes(..))",
            returning = "result")
    public void collectParamPreProcessResult(Object result) {
        try {
            LogContext context = InvocationLogContextHolder.getContext();
            if (context != null) {
                context.setParamBeforeInvoke((String) result);
            }
        } catch (Exception e) {
            log.error("收集参数预处理结果失败", e);
        }
    }

}
