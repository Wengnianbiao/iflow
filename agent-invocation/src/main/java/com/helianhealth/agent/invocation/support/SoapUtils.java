package com.helianhealth.agent.invocation.support;

import com.helianhealth.agent.common.base.MetaInfoKeyConstants;
import com.helianhealth.agent.common.exception.AgentConfigException;
import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentInvocationException;
import com.helianhealth.agent.common.utils.XmlUtils;
import com.helianhealth.agent.invocation.remote.manager.HttpApiClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class SoapUtils {

    private static final Logger log = LoggerFactory.getLogger(SoapUtils.class);

    private static final HttpApiClientManager httpApiClientManager = HttpApiClientManager.getInstance();

    /**
     * 通过标准 HTTP 底座发送 SOAP 请求
     * @param metaInfo Map，包含请求地址、方法、请求头等信息
     * @param soapMessage SOAP消息
     * @return SOAP响应
     */
    public static String sendSoapRequest(Map<String, Object> metaInfo, String soapMessage) {
        try {
            String endpointUrl = (String) metaInfo.get(MetaInfoKeyConstants.URL);
            if (endpointUrl == null || endpointUrl.isEmpty()) {
                throw new AgentConfigException(AgentErrorCode.WS_CONFIG_MISSING, "metaInfo中未配置url");
            }

            Map<String, String> headers = buildSoapHeaders(metaInfo);
            return httpApiClientManager.executePost(endpointUrl, headers, soapMessage);
        } catch (Exception e) {
            log.error("发送SOAP请求失败", e);
            throw new AgentInvocationException(AgentErrorCode.WS_CALL_FAILED, e.getMessage(), e);
        }
    }

    /**
     * 通过兼容型通道发送 SOAP 请求
     * 适用于对 HttpURLConnection 更兼容的 SOAP 服务
     *
     * @param metaInfo Map，包含请求地址、请求头等信息
     * @param soapMessage SOAP消息
     * @return SOAP响应
     */
    public static String sendSoapRequestCompatible(Map<String, Object> metaInfo, String soapMessage) {
        try {
            String endpointUrl = (String) metaInfo.get(MetaInfoKeyConstants.URL);
            if (endpointUrl == null || endpointUrl.isEmpty()) {
                throw new AgentConfigException(AgentErrorCode.WS_CONFIG_MISSING, "metaInfo中未配置url");
            }

            Map<String, String> headers = buildSoapHeaders(metaInfo);
            return sendSoapRequestWithUrlConnection(endpointUrl, headers, soapMessage);
        } catch (Exception e) {
            log.error("发送SOAP兼容请求失败", e);
            throw new AgentInvocationException(AgentErrorCode.WS_CALL_FAILED, e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> buildSoapHeaders(Map<String, Object> metaInfo) {
        Map<String, String> headers = new HashMap<>();
        Map<String, String> headerConfig = (Map<String, String>) metaInfo.get(MetaInfoKeyConstants.HEADERS);
        if (headerConfig != null) {
            headers.putAll(headerConfig);
        }
        if (!containsHeaderIgnoreCase(headers, "Content-Type")) {
            headers.put("Content-Type", "text/xml;charset=UTF-8");
        }
        return headers;
    }

    private static boolean containsHeaderIgnoreCase(Map<String, String> headers, String headerName) {
        for (String key : headers.keySet()) {
            if (headerName.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private static String sendSoapRequestWithUrlConnection(String endpointUrl,
                                                           Map<String, String> headers,
                                                           String soapMessage) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(endpointUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            headers.forEach(conn::setRequestProperty);

            byte[] body = soapMessage.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(body.length));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
                os.flush();
            }

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) {
                throw new IOException("SOAP请求无响应，HTTP状态码: " + status);
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            String responseBody = sb.toString();
            if (status < 200 || status >= 300) {
                log.error("SOAP兼容通道返回非成功状态码: {}, 响应: {}", status, responseBody);
            }
            return responseBody;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 解析SOAP/XML响应
     * @param responseXml 原始XML响应字符串
     * @param metaInfo 元信息配置，支持以下key：
     *                 elementName - 业务数据标签名定位（如SOAP Body内的目标节点）
     *                 isCDATA - 是否从CDATA/文本内容中提取，默认true
     *                 dataType - 内容类型（json/xml），默认xml
     *                 innerElementName - XML内容中的二级标签定位
     *                 escape - 是否先反转义再解析
     *                 objectTag - 指定按对象解析的标签名集合
     * @return 解析后的Map
     */
    public static Map<String, Object> parseSoapResponse(String responseXml, Map<String, Object> metaInfo) {
        try {
            return XmlUtils.parseResponseXml(responseXml, metaInfo != null ? metaInfo : new HashMap<>());
        } catch (Exception e) {
            log.error("解析SOAP响应失败", e);
            throw new AgentInvocationException(AgentErrorCode.WS_RESPONSE_PARSE_ERROR, e.getMessage(), e);
        }
    }
}
