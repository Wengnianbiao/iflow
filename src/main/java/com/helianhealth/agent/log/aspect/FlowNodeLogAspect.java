package com.helianhealth.agent.log.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.model.domain.InterfaceWorkflowNodeDO;
import com.helianhealth.agent.model.dto.LogContext;
import com.helianhealth.agent.service.InterfaceInvokeLogService;
import com.helianhealth.agent.utils.JsonUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
@Component
@Slf4j
public class FlowNodeLogAspect {

    private static final ThreadLocal<LogContext> logContext = new ThreadLocal<>();

    /**
     * 环绕整个executeFlowNode流程，在最后阶段统一保存日志
     */
    /**
     * 环绕AbstractClientProxy的remoteInvoke方法，收集完整的调用信息
     */

    /**
     * 收集参数预处理结果
     */
    @AfterReturning(
            pointcut = "execution(* com.helianhealth.agent.remote.ParamResolver.resolveParamNodes(..))",
            returning = "result")
    public void collectParamPreProcessResult(Object result) {
        try {
            LogContext context = logContext.get();
            context.setParamBeforeInvoke((String) result);
        } catch (Exception e) {
            log.error("收集参数预处理结果失败", e);
        }
    }

}

