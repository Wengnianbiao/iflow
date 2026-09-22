package com.helianhealth.iflow.tasks.http;

import java.net.InetAddress;

/**
 * HttpTask 辅助工具
 */
public final class HttpTaskUtils {

    private static final String SERVER_ID = resolveServerId();

    private HttpTaskUtils() {
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
