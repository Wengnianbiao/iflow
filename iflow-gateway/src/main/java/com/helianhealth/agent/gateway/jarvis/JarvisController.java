package com.helianhealth.agent.gateway.jarvis;

import com.helianhealth.agent.gateway.jarvis.model.JarvisRequest;
import com.helianhealth.agent.gateway.jarvis.model.JarvisResultData;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JarvisController {

    private final JarvisService jarvisService;

    public JarvisController(JarvisService jarvisService) {
        this.jarvisService = jarvisService;
    }

    @PostMapping("/HLOpenApi/Hjk")
    public JarvisResultData<Object> invokeJarvisApi(@RequestBody JarvisRequest request) {
        return jarvisService.invoke(request);
    }
}
