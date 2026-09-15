package com.helianhealth.agent.tasks.webservice.providers;

import com.helianhealth.agent.tasks.webservice.WebServiceTask;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 默认 {@link SoapClientProvider}：按 {@link WebServiceTask.Input} 超时参数每次新建
 * {@link CloseableHttpClient}，调用方负责 close。
 *
 * <p>任务未指定超时时使用系统默认；任务入参 {@code connectionTimeOut}/{@code readTimeOut} 可覆盖。
 */
@Component
public class DefaultSoapClientProvider implements SoapClientProvider {

    static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    static final int DEFAULT_READ_TIMEOUT_MS = 30000;

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
        return Optional.ofNullable(input.getConnectionTimeOut()).orElse(DEFAULT_CONNECT_TIMEOUT_MS);
    }

    @Override
    public int resolveReadTimeoutMs(WebServiceTask.Input input) {
        return Optional.ofNullable(input.getReadTimeOut()).orElse(DEFAULT_READ_TIMEOUT_MS);
    }
}
