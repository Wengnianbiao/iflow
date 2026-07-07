package com.helianhealth.agent.gateway.rest;

import com.helianhealth.agent.common.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
public class OpenApiController {

    private final OpenApiService openApiService;

    public OpenApiController(OpenApiService openApiService) {
        this.openApiService = openApiService;
    }

    @RequestMapping(
            value = "/agent-open-api/**",
            method = RequestMethod.POST,
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE,
                    MediaType.TEXT_XML_VALUE, MediaType.TEXT_PLAIN_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE}
    )
    @ResponseBody
    public String invokeOpenApi(@RequestBody(required = false) String request, HttpServletRequest httpRequest) {
        return openApiService.invoke(request, httpRequest.getRequestURI());
    }

    @RequestMapping(
            value = "/agent-open-api/**",
            method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE}
    )
    @ResponseBody
    public String invokeOpenApiGet(HttpServletRequest httpRequest) {
        Map<String, String[]> paramMap = httpRequest.getParameterMap();
        Map<String, Object> params = new HashMap<>();
        paramMap.forEach((k, v) -> params.put(k, v.length == 1 ? v[0] : v));
        return openApiService.invoke(JsonUtils.toJsonString(params), httpRequest.getRequestURI());
    }
}
