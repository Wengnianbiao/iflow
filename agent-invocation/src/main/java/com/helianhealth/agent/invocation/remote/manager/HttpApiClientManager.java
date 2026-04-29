package com.helianhealth.agent.invocation.remote.manager;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HttpApiClientManager {

    private static volatile HttpApiClientManager instance;
    private final PoolingHttpClientConnectionManager connectionManager;
    private final CloseableHttpClient httpClient;
    private static final Logger logger = LoggerFactory.getLogger(HttpApiClientManager.class);

    // 默认连接超时时间，单位毫秒
    private static final int DEFAULT_CONNECTION_TIMEOUT = 20000;
    // 默认请求超时时间，单位毫秒
    private static final int DEFAULT_REQUEST_TIMEOUT = 20000;
    // 从连接池获取连接的超时时间，单位毫秒
    private static final int DEFAULT_CONNECTION_REQUEST_TIMEOUT = 1000;

    private static final int CLIENT_CONN_MAX_IDLE_MILLISECONDS = 30000;

    // 默认验证空闲连接时间1秒
    private static final int DEFAULT_VALIDATE_AFTER_INACTIVITY_MILLISECONDS = 1000;

    private static final Map<String, String> DEFAULT_HEADERS = new HashMap<>();
    static {
        DEFAULT_HEADERS.put("Content-Type", "application/json");
    }

    // 私有构造函数，防止外部实例化
    private HttpApiClientManager() {
        // 创建信任所有证书的 SSL 上下文
        SSLContext sslContext;
        try {
            // 信任所有证书（跳过证书验证）
            TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
            sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, acceptingTrustStrategy)
                    .build();
            logger.info("HTTPS certificate trust strategy enabled, skipping certificate verification");
        } catch (Exception e) {
            logger.warn("Failed to create custom SSL context, using system default configuration", e);
            sslContext = SSLContexts.createSystemDefault();
        }
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                sslContext,
                (hostname, session) -> true
        );

        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslSocketFactory)
                .build();

        connectionManager = new PoolingHttpClientConnectionManager(
                socketFactoryRegistry,
                null, //  poolConcurrencyPolicy 可传null使用默认
                null, //  poolReusePolicy 可传null使用默认
                TimeValue.of(CLIENT_CONN_MAX_IDLE_MILLISECONDS, TimeUnit.MILLISECONDS) // 连接最大存活时间
        );

        // 设置连接池参数
        connectionManager.setMaxTotal(200); // 最大连接数
        connectionManager.setDefaultMaxPerRoute(20); // 每个路由默认最大连接数
        connectionManager.setValidateAfterInactivity(Timeout.ofMilliseconds(DEFAULT_VALIDATE_AFTER_INACTIVITY_MILLISECONDS));

        // 创建请求配置
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(DEFAULT_CONNECTION_TIMEOUT))        // 连接超时时间
                .setResponseTimeout(Timeout.ofMilliseconds(DEFAULT_REQUEST_TIMEOUT))        // 读取超时时间
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(DEFAULT_CONNECTION_REQUEST_TIMEOUT)) // 从连接池获取连接的超时时间
                .build();

        // 创建HttpClient实例
        httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    public static HttpApiClientManager getInstance() {
        if (instance == null) {
            synchronized (HttpApiClientManager.class) {
                if (instance == null) {
                    instance = new HttpApiClientManager();
                }
            }
        }
        return instance;
    }

    /**
     * 执行HTTP POST请求
     * @param url 请求URL
     * @param requestBody 请求体
     * @return 响应内容
     * @throws IOException IO异常
     */
    public String executePost(String url,  Map<String, String> headers, String requestBody) throws IOException, ParseException {
        logger.info("执行 POST 请求，URL: [{}]，请求体: [{}]", url, requestBody);
        HttpPost httpPost = new HttpPost(encodeUrl(url));
        // 设置请求头
        if (headers != null && !headers.isEmpty()) {
            headers.forEach(httpPost::setHeader);
        } else {
            headers = DEFAULT_HEADERS;
            headers.forEach(httpPost::setHeader);
        }
        if (requestBody != null && !requestBody.isEmpty()) {
            ContentType entityContentType = resolveContentType(headers);
            httpPost.setEntity(new StringEntity(requestBody, entityContentType));
        }
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            String responseEntity = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            logger.info("POST 请求响应: [{}]", responseEntity);
            return responseEntity;
        } catch (IOException | ParseException e) {
            logger.debug("执行 POST 请求失败，URL: [{}]，异常: [{}]", url, e.getMessage());
            throw e;
        }
    }

    /**
     * 执行HTTP GET请求
     * @param url 请求URL
     * @return 响应内容
     * @throws IOException IO异常
     */
    public String executeGet(String url,  Map<String, String> headers) throws IOException, ParseException {
        logger.info("执行 GET 请求，URL: [{}]", url);
        HttpGet httpGet = new HttpGet(encodeUrl(url));
        // 设置请求头
        if (headers != null && !headers.isEmpty()) {
            headers.forEach(httpGet::setHeader);
        } else {
            headers = DEFAULT_HEADERS;
            headers.forEach(httpGet::setHeader);
        }

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getCode();
            logger.info("GET 请求响应状态码: [{}]", statusCode);
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (IOException | ParseException e) {
            logger.debug("执行 GET 请求失败，URL: [{}]，异常: [{}]", url, e.getMessage());
            throw e;
        }
    }

    /**
     * 执行 application/x-www-form-urlencoded 格式的 POST 请求
     * @param url 请求URL
     * @param headers 自定义请求头（可为null）
     * @param formParams 表单参数
     * @return 响应内容
     * @throws IOException IO异常
     * @throws ParseException 解析异常
     */
    public String executePostForm(String url, Map<String, String> headers, Map<String, String> formParams) throws IOException, ParseException {
        logger.info("执行 POST Form 请求，URL: [{}]，参数: [{}]", url, formParams);
        HttpPost httpPost = new HttpPost(encodeUrl(url));

        // 设置请求头（默认 application/x-www-form-urlencoded）
        if (headers != null && !headers.isEmpty()) {
            headers.forEach(httpPost::setHeader);
        }
        // 确保 Content-Type 为 application/x-www-form-urlencoded
        if (headers == null || !headers.containsKey("Content-Type")) {
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
        }

        // 构建表单参数
        if (formParams != null && !formParams.isEmpty()) {
            List<NameValuePair> params = new ArrayList<>();
            formParams.forEach((key, value) -> params.add(new BasicNameValuePair(key, value)));
            httpPost.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
        }

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            String responseEntity = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            logger.info("POST Form 请求响应: [{}]", responseEntity);
            return responseEntity;
        } catch (IOException | ParseException e) {
            logger.debug("执行 POST Form 请求失败，URL: [{}]，异常: [{}]", url, e.getMessage());
            throw e;
        }
    }

    /**
     * 执行HTTP GET请求并返回字节数组（用于下载二进制文件）
     * @param url 请求URL
     * @param headers 请求头
     * @return 文件的字节数组
     * @throws IOException IO异常
     */
    public byte[] executeGetBytes(String url, Map<String, String> headers) throws IOException {
        logger.info("执行 GET 请求下载二进制文件，URL: [{}]", url);
        HttpGet httpGet = new HttpGet(encodeUrl(url));
        // 设置请求头
        if (headers != null && !headers.isEmpty()) {
            headers.forEach(httpGet::setHeader);
        }

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getCode();
            if (statusCode == 200) {
                byte[] bytes = EntityUtils.toByteArray(response.getEntity());
                logger.info("GET 请求下载成功，URL: [{}]，文件大小: {} bytes", url, bytes.length);
                return bytes;
            } else {
                logger.error("GET 请求下载失败，URL: [{}]，状态码: [{}]", url, statusCode);
                throw new IOException("下载文件失败，状态码: " + statusCode);
            }
        } catch (IOException e) {
            logger.error("执行 GET 请求下载失败，URL: [{}]，异常: [{}]", url, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 获取连接池状态信息
     * @return 连接池状态字符串
     */
    public String getPoolStats() {
        return connectionManager.getTotalStats().toString();
    }

    /**
     * 根据请求头中的 Content-Type 解析实体内容类型
     * @param headers 请求头
     * @return 对应的 ContentType，默认 application/json;charset=UTF-8
     */
    private ContentType resolveContentType(Map<String, String> headers) {
        if (headers != null && !headers.isEmpty()) {
            String ct = getHeaderIgnoreCase(headers, "Content-Type");
            if (ct != null && !ct.isEmpty()) {
                try {
                    return ContentType.parse(ct);
                } catch (Exception e) {
                    logger.warn("Content-Type解析失败: [{}]，使用默认JSON", ct);
                }
            }
        }
        return ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8);
    }

    /**
     * 大小写无关地读取请求头值
     */
    private String getHeaderIgnoreCase(Map<String, String> headers, String headerName) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (headerName.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 对 URL 进行编码，处理中文等非 ASCII 字符
     * <p>
     * java.net.URI 不接受未编码的非 ASCII 字符，直接传入会抛出 IllegalArgumentException。
     * 此方法通过 URI 多参数构造器自动对各部分进行 RFC 2396 编码，再转为 ASCII 字符串。
     *
     * @param url 原始 URL（可能包含中文等非 ASCII 字符）
     * @return 编码后的 URI
     */
    private URI encodeUrl(String url) {
        try {
            // 利用 java.net.URL 解析原始 URL 的各个部分
            URL parsed = new URL(url);
            // URI 多参数构造器会对 path、query、fragment 中的非 ASCII 字符进行编码，
            // 再转换为 ASCII 形式，确保请求行不会携带原始中文路径。
            URI encodedUri = new URI(parsed.getProtocol(), parsed.getAuthority(),
                    parsed.getPath(), parsed.getQuery(), parsed.getRef());
            return URI.create(encodedUri.toASCIIString());
        } catch (Exception e) {
            logger.error("URL编码失败，使用原始URL: [{}]", url, e);
            return URI.create(url);
        }
    }

    /**
     * 关闭连接池和相关资源
     */
    public void close() {
        try {
            httpClient.close();
            connectionManager.close();
        } catch (Exception e) {
            logger.error("Error closing HTTP client resources", e);
        }
    }
}
