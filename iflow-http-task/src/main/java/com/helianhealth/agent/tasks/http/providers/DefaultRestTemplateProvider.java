package com.helianhealth.agent.tasks.http.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.tasks.http.HttpTask;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * 默认 {@link RestTemplateProvider}：按任务入参超时配置，每次调用新建 {@link RestTemplate}。
 *
 * <p>任务未指定超时时使用系统默认；任务入参 {@code connectionTimeOut}/{@code readTimeOut} 可覆盖。
 */
@Component
public class DefaultRestTemplateProvider implements RestTemplateProvider {

    static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    static final int DEFAULT_READ_TIMEOUT_MS = 30000;

    private final ObjectMapper objectMapper;

    public DefaultRestTemplateProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public @NonNull RestTemplate getRestTemplate(@NonNull HttpTask.Input input) {
        int readMs = Optional.ofNullable(input.getReadTimeOut()).orElse(DEFAULT_READ_TIMEOUT_MS);
        int connectMs = Optional.ofNullable(input.getConnectionTimeOut()).orElse(DEFAULT_CONNECT_TIMEOUT_MS);

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
