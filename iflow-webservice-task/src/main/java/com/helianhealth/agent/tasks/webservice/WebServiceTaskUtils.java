package com.helianhealth.agent.tasks.webservice;

import java.net.InetAddress;

/**
 * WebServiceTask 辅助工具（对齐 {@link com.helianhealth.agent.tasks.http.HttpTaskUtils}）。
 */
public final class WebServiceTaskUtils {

    private static final String SERVER_ID = resolveServerId();

    private WebServiceTaskUtils() {
    }

    public static String getServerId() {
        return SERVER_ID;
    }

    private static String resolveServerId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
