package com.helianhealth.iflow.tasks.http.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.tasks.http.HttpTask;
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
 * 默认 {@link RestTemplateProvider}：按任务入参超时配置，每次调用新建 {@link RestTemplate}。
 *
 * <p>任务入参 {@code connectionTimeOut}/{@code readTimeOut} 优先。未指定时使用
 * {@code iflow.tasks.http.connect-timeout}（默认 5000ms）和
 * {@code iflow.tasks.http.read-timeout}（默认 30000ms）。
 */
@Component
public class DefaultRestTemplateProvider implements RestTemplateProvider {

    private final ObjectMapper objectMapper;
    private final int defaultReadTimeout;
    private final int defaultConnectTimeout;

    public DefaultRestTemplateProvider(
            ObjectMapper objectMapper,
            @Value("${iflow.tasks.http.read-timeout:30000ms}") Duration readTimeout,
            @Value("${iflow.tasks.http.connect-timeout:5000ms}") Duration connectTimeout) {
        this.objectMapper = objectMapper;
        this.defaultReadTimeout = (int) readTimeout.toMillis();
        this.defaultConnectTimeout = (int) connectTimeout.toMillis();
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
