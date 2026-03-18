package com.helianhealth.agent.console.controller;

import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import com.helianhealth.agent.console.controller.request.HttpProxyRequest;
import com.helianhealth.agent.console.controller.reponse.HttpProxyResponse;
import com.helianhealth.agent.console.controller.reponse.SqlExecuteResponse;
import com.helianhealth.agent.console.controller.request.SqlExecuteRequest;
import com.helianhealth.agent.console.service.HttpProxyService;
import com.helianhealth.agent.console.service.SqlExecuteService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 调用工具控制器
 * 提供HTTP代理调用、数据库查询等功能
 */
@RestController
@RequestMapping("/v1/console/invoke-tool")
@Slf4j
@AllArgsConstructor
public class InvokeToolController {

    private final HttpProxyService httpProxyService;
    private final SqlExecuteService sqlExecuteService;

    /**
     * HTTP代理调用
     * 用于从后端发起HTTP请求，绕过浏览器CORS限制
     */
    @PostMapping("/proxy")
    public ResultData<HttpProxyResponse> proxyInvoke(@RequestBody HttpProxyRequest request) {
        try {
            log.info("HTTP代理请求: {} {}", request.getMethod(), request.getUrl());
            HttpProxyResponse response = httpProxyService.execute(request);
            log.info("HTTP代理响应: status={}", response.getStatus());
            return ResponseRenderUtils.render(response);
        } catch (IllegalArgumentException e) {
            log.warn("HTTP代理请求参数错误: {}", e.getMessage());
            return ResponseRenderUtils.error(e.getMessage());
        } catch (Exception e) {
            log.error("HTTP代理请求失败", e);
            return ResponseRenderUtils.error("请求失败: " + e.getMessage());
        }
    }

    /**
     * 执行SQL查询
     * 仅支持SELECT语句
     */
    @PostMapping("/db/execute")
    public ResultData<SqlExecuteResponse> executeSql(@RequestBody SqlExecuteRequest request) {
        try {
            log.info("SQL查询请求: dbType={}, url={}", request.getDbType(), request.getUrl());
            SqlExecuteResponse response = sqlExecuteService.executeQuery(request);
            return ResponseRenderUtils.render(response);
        } catch (IllegalArgumentException e) {
            log.warn("SQL查询参数错误: {}", e.getMessage());
            return ResponseRenderUtils.error(e.getMessage());
        } catch (Exception e) {
            log.error("SQL查询失败", e);
            return ResponseRenderUtils.error("查询失败: " + e.getMessage());
        }
    }
}
