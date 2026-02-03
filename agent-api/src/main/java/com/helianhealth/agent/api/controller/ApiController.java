package com.helianhealth.agent.api.controller;

import com.helianhealth.agent.api.model.request.JarvisRequest;
import com.helianhealth.agent.api.service.ApiService;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
@AllArgsConstructor
public class ApiController {

    private final ApiService apiService;

    @GetMapping("/HLOpenApi/Hjk")
    public ResultData<Object> healthCheck() {
        return ResponseRenderUtils.success();
    }

    @PostMapping("/HLOpenApi/Hjk")
    public ResultData<Object> invokeJarvisApi(@RequestBody JarvisRequest request) {
        return apiService.invokeJarvisApi(request);
    }

    @RequestMapping(
            value = "/agent-open-api/**",
            method = RequestMethod.POST,
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE,
                    MediaType.TEXT_XML_VALUE, MediaType.TEXT_PLAIN_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE}
    )
    @ResponseBody
    public String invokeOpenApi(@RequestBody String request, HttpServletRequest httpRequest) {
        String interfaceUri = httpRequest.getRequestURI();
        return apiService.invokeOpenApi(request, interfaceUri);
    }

    @RequestMapping(
            value = "/agent-open-api/**",
            method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE}
    )
    @ResponseBody
    public String invokeOpenApiGet(HttpServletRequest httpRequest) {
        String interfaceUri = httpRequest.getRequestURI();

        // Query 参数转 Map → JSON
        Map<String, String[]> paramMap = httpRequest.getParameterMap();
        Map<String, Object> params = new HashMap<>();
        paramMap.forEach((k, v) -> params.put(k, v.length == 1 ? v[0] : v));
        String requestBody = JsonUtils.toJsonString(params);

        return apiService.invokeOpenApi(requestBody, interfaceUri);
    }
}
