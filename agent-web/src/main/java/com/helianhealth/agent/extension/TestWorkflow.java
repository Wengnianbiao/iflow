package com.helianhealth.agent.extension;

import com.helianhealth.agent.api.OpenApiRouteHandler;
import com.helianhealth.agent.repository.model.domain.Workflow;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestWorkflow implements OpenApiRouteHandler {

    @Override
    public String handle(Workflow workflow, String requestBody) {
        log.info("handle workflow: {}", workflow);
        log.info("request body: {}", requestBody);
        log.info("被调用！！");
        return null;
    }
}
