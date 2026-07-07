package com.helianhealth.agent.tasks.http.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.tasks.http.HttpTask;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * 默认 {@link RestTemplateProvider}：按任务入参（或全局默认）超时配置，每次调用新建 {@link RestTemplate}。
 */
@Component
public class DefaultRestTemplateProvider implements RestTemplateProvider {

    private final int defaultReadTimeout;
    private final int defaultConnectTimeout;
    private final ObjectMapper objectMapper;

    public DefaultRestTemplateProvider(
            @Value("${iflow.tasks.http.readTimeout:3000ms}") Duration readTimeout,
            @Value("${iflow.tasks.http.connectTimeout:3000ms}") Duration connectTimeout,
            ObjectMapper objectMapper) {
        this.defaultReadTimeout = (int) readTimeout.toMillis();
        this.defaultConnectTimeout = (int) connectTimeout.toMillis();
        this.objectMapper = objectMapper;
    }

    @Override
    public @NonNull RestTemplate getRestTemplate(@NonNull HttpTask.Input input) {
        int readMs = Optional.ofNullable(input.getReadTimeOut()).orElse(defaultReadTimeout);
        int connectMs = Optional.ofNullable(input.getConnectionTimeOut()).orElse(defaultConnectTimeout);

        RequestConfig requestConfig =
                RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofMilliseconds(readMs))
                        .build();
        HttpClient httpClient =
                HttpClients.custom().setDefaultRequestConfig(requestConfig).build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout(connectMs);
        requestFactory.setReadTimeout(readMs);

        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.getMessageConverters().removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
        restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter(objectMapper));
        return restTemplate;
    }
}
