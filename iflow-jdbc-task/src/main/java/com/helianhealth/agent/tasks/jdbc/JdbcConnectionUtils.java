package com.helianhealth.agent.tasks.jdbc;

import java.util.Properties;

/**
 * JDBC 连接属性工具（无池化，单次建连用）。
 */
public final class JdbcConnectionUtils {

    private JdbcConnectionUtils() {
    }

    public static Properties buildConnectionProperties(String username, String password, int connectTimeoutMs, int queryTimeoutMs) {
        Properties props = new Properties();
        if (username != null) {
            props.setProperty("user", username);
        }
        if (password != null) {
            props.setProperty("password", password);
        }
        applyTimeoutProperties(props, connectTimeoutMs, queryTimeoutMs);
        return props;
    }

    static void applyTimeoutProperties(Properties props, int connectTimeoutMs, int queryTimeoutMs) {
        props.setProperty("connectTimeout", String.valueOf(connectTimeoutMs));
        props.setProperty("socketTimeout", String.valueOf(queryTimeoutMs));
        props.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(connectTimeoutMs));
        props.setProperty("oracle.jdbc.ReadTimeout", String.valueOf(queryTimeoutMs));
    }

    public static int toQueryTimeoutSeconds(int queryTimeoutMs) {
        return Math.max(1, (queryTimeoutMs + 999) / 1000);
    }
}
