package com.helianhealth.agent.utils;

import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SoapUtils {

    private static final Logger log = LoggerFactory.getLogger(SoapUtils.class);

    public static String sendSoapRequest(JSONObject metaInfo, String soapMessage) {
        try {
            // 解析metaInfo获取请求地址
            String endpointUrl = metaInfo.getString("url");

            if (StringUtils.isEmpty(endpointUrl)) {
                throw new IllegalArgumentException("metaInfo中未配置url");
            }

            // 创建HTTP连接
            URL url = new URL(endpointUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // 设置请求头
            connection.setRequestMethod(metaInfo.getString("method"));
            connection.setDoOutput(true);

            // 设置自定义请求头
            JSONObject headers = metaInfo.getJSONObject("headers");
            if (headers != null) {
                for (String key : headers.keySet()) {
                    connection.setRequestProperty(key, headers.getString(key));
                }
            }

            // 如果没有设置Content-Type，使用默认值
            if (headers == null || !headers.containsKey("Content-Type")) {
                connection.setRequestProperty("Content-Type", "text/xml;charset=UTF-8");
            }

            // 发送SOAP消息
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = soapMessage.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // 读取响应
            StringBuilder response = getStringBuilder(connection);

            // 处理错误响应
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                throw new RuntimeException("SOAP请求失败，响应码: " + connection.getResponseCode() +
                        ", 响应信息: " + response);
            }

            return response.toString();
        } catch (Exception e) {
            log.error("发送SOAP请求失败", e);
            throw new RuntimeException("发送SOAP请求失败: " + e.getMessage());
        }
    }

    private static StringBuilder getStringBuilder(HttpURLConnection connection) throws IOException {
        StringBuilder response = new StringBuilder();
        try (InputStream is = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } catch (Exception e) {
            try (InputStream es = connection.getErrorStream()) {
                if (es != null) {
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            response.append(line);
                        }
                    }
                }
            }
        }
        return response;
    }
}
