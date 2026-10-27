package com.helianhealth.iflow.gateway.jarvis;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JarvisController {

    private final JarvisService jarvisService;
    private final JarvisRequestReader requestReader;

    public JarvisController(JarvisService jarvisService, JarvisRequestReader requestReader) {
        this.jarvisService = jarvisService;
        this.requestReader = requestReader;
    }

    @PostMapping(value = "/HLOpenApi/Hjk", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
    public Object invokeJarvisApi(HttpServletRequest request) {
        return jarvisService.invoke(requestReader.read(request));
    }
}
