package com.helianhealth.iflow.gateway.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** HTTP 工作流入口；控制台、静态资源和 Jarvis 使用各自的处理器。 */
@Component
final class OpenApiPaths {

    static final String MAPPING = "/{segment:(?!(?:api|assets|error|actuator|HLOpenApi|"
            + "index\\.html|favicon\\.svg)$).+}/**";

    private static final PathPattern PATTERN = PathPatternParser.defaultInstance.parse(MAPPING);

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMapping;

    OpenApiPaths(
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    static String pathWithinApplication(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    boolean matches(HttpServletRequest request) {
        String method = request.getMethod();
        if (!("GET".equals(method) || "POST".equals(method) || "HEAD".equals(method))
                || !PATTERN.matches(PathContainer.parsePath(pathWithinApplication(request)))) {
            return false;
        }
        try {
            // 只查询处理器，不执行拦截器；避免限制宿主应用的其他 Controller。
            HandlerExecutionChain chain = handlerMapping.getObject().getHandler(request);
            return chain != null && chain.getHandler() instanceof HandlerMethod handler
                    && OpenApiController.class.isAssignableFrom(handler.getBeanType());
        } catch (Exception e) {
            // 405 / 406 等路由错误由 DispatcherServlet 正常处理。
            return false;
        }
    }
}
