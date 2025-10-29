package com.helianhealth.agent.log.aspect;

import com.helianhealth.agent.log.context.LogContextHolder;
import com.helianhealth.agent.model.dto.LogContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class FlowNodeLogAspect {

    /**
     * 收集参数预处理结果
     */
    @AfterReturning(
            pointcut = "execution(* com.helianhealth.agent.remote.ParamResolver.resolveParamNodes(..))",
            returning = "result")
    public void collectParamPreProcessResult(Object result) {
        try {
            LogContext context = LogContextHolder.getContext();
            if (context != null) {
                context.setParamBeforeInvoke((String) result);
            }
        } catch (Exception e) {
            log.error("收集参数预处理结果失败", e);
        }
    }

}

