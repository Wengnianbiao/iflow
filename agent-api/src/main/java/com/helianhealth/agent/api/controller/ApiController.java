package com.helianhealth.agent.api.controller;

import com.helianhealth.agent.api.model.request.JarvisRequest;
import com.helianhealth.agent.api.service.ApiService;
import com.helianhealth.agent.common.ResultData;
import com.helianhealth.agent.utils.ResponseRenderUtils;
import io.swagger.annotations.ApiOperation;
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

@RestController
@Slf4j
@AllArgsConstructor
public class ApiController {

    private final ApiService apiService;

    @ApiOperation(value = "健康检查")
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
        return apiService.invokeOpenApi(request, httpRequest);
    }
}
