package com.helianhealth.agent.tasks.webservice.providers;

import com.helianhealth.agent.tasks.webservice.WebServiceTask;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 默认 {@link SoapClientProvider}：按 {@link WebServiceTask.Input} 超时参数每次新建
 * {@link CloseableHttpClient}，调用方负责 close。
 */
@Component
public class DefaultSoapClientProvider implements SoapClientProvider {

    private final int defaultConnectTimeoutMs;
    private final int defaultReadTimeoutMs;

    public DefaultSoapClientProvider(
            @Value("${iflow.tasks.webservice.connectTimeout:5000ms}") Duration connectTimeout,
            @Value("${iflow.tasks.webservice.readTimeout:5000ms}") Duration readTimeout) {
        this.defaultConnectTimeoutMs = (int) connectTimeout.toMillis();
        this.defaultReadTimeoutMs = (int) readTimeout.toMillis();
    }

    public DefaultSoapClientProvider(int defaultConnectTimeoutMs, int defaultReadTimeoutMs) {
        this.defaultConnectTimeoutMs = defaultConnectTimeoutMs;
        this.defaultReadTimeoutMs = defaultReadTimeoutMs;
    }

    @Override
    public @NonNull CloseableHttpClient createHttpClient(@NonNull WebServiceTask.Input input) {
        int connectMs = resolveConnectTimeoutMs(input);
        int readMs = resolveReadTimeoutMs(input);
        RequestConfig requestConfig =
                RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectMs))
                        .setResponseTimeout(Timeout.ofMilliseconds(readMs))
                        .build();
        return HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
    }

    @Override
    public int resolveConnectTimeoutMs(WebServiceTask.Input input) {
        return Optional.ofNullable(input.getConnectionTimeOut()).orElse(defaultConnectTimeoutMs);
    }

    @Override
    public int resolveReadTimeoutMs(WebServiceTask.Input input) {
        return Optional.ofNullable(input.getReadTimeOut()).orElse(defaultReadTimeoutMs);
    }
}
