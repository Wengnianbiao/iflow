package com.helianhealth.iflow.gateway.rest;

import com.helianhealth.iflow.gateway.jarvis.JarvisController;
import com.helianhealth.iflow.gateway.jarvis.JarvisRequestReader;
import com.helianhealth.iflow.gateway.jarvis.JarvisServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Servlet 入站网关：Open API 与 Jarvis。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({OpenApiController.class, OpenApiServiceImpl.class, OpenApiRequestReader.class,
        OpenApiPaths.class, OpenApiBodyLimitFilter.class, OpenApiMultipartExceptionHandler.class,
        JarvisController.class, JarvisServiceImpl.class, JarvisRequestReader.class})
public class GatewayConfiguration {
}
