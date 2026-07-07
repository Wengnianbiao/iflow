package com.helianhealth.agent.tasks.http.providers;

import com.helianhealth.agent.tasks.http.HttpTask;
import org.springframework.lang.NonNull;
import org.springframework.web.client.RestTemplate;

@FunctionalInterface
public interface RestTemplateProvider {

    RestTemplate getRestTemplate(@NonNull HttpTask.Input input);
}
