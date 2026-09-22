package com.helianhealth.iflow.tasks.webservice.providers;

import com.helianhealth.iflow.tasks.webservice.WebServiceTask;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

/**
 * SOAP HTTP 客户端工厂：按任务入参每次新建 {@link CloseableHttpClient}，不做池化。
 */
public interface SoapClientProvider {

    CloseableHttpClient createHttpClient(WebServiceTask.Input input);

    /** 解析建连超时（毫秒）：任务入参优先，否则用全局默认。 */
    int resolveConnectTimeoutMs(WebServiceTask.Input input);

    /** 解析响应读取超时（毫秒）：任务入参优先，否则用全局默认。 */
    int resolveReadTimeoutMs(WebServiceTask.Input input);
}
