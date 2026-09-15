package com.helianhealth.agent.tasks.jdbc.providers;

import com.helianhealth.agent.tasks.jdbc.JdbcConnectionUtils;
import com.helianhealth.agent.tasks.jdbc.JdbcTask;
import com.helianhealth.agent.tasks.jdbc.JdbcVendor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;

/**
 * 默认 {@link JdbcConnectionProvider}：按 {@link JdbcTask.Input} 中的 URL、账号、厂商
 * 及超时参数，每次调用通过 {@link DriverManager} 新建连接。驱动类名由 {@link JdbcVendor} 映射。
 *
 * <p>任务未指定超时时使用系统默认；任务入参 {@code connectionTimeOut}/{@code queryTimeOut} 可覆盖。
 */
@Component
public class DefaultJdbcConnectionProvider implements JdbcConnectionProvider {

    public DefaultJdbcConnectionProvider() {
    }

    static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    static final int DEFAULT_QUERY_TIMEOUT_MS = 30000;

    @Override
    public @NonNull Connection openConnection(@NonNull JdbcTask.Input input) throws SQLException {
        String driverClassName = JdbcVendor.resolveDriverClassName(input.getVendor());
        try {
            Class.forName(driverClassName);
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not found: " + driverClassName, e);
        }

        int connectMs = resolveConnectTimeoutMs(input);
        int queryMs = resolveQueryTimeoutMs(input);
        Properties props = JdbcConnectionUtils.buildConnectionProperties(
                input.getUsername(), input.getPassword(), connectMs, queryMs);

        return DriverManager.getConnection(input.getUrl(), props);
    }

    @Override
    public int resolveConnectTimeoutMs(JdbcTask.Input input) {
        return Optional.ofNullable(input.getConnectionTimeOut()).orElse(DEFAULT_CONNECT_TIMEOUT_MS);
    }

    @Override
    public int resolveQueryTimeoutMs(JdbcTask.Input input) {
        return Optional.ofNullable(input.getQueryTimeOut()).orElse(DEFAULT_QUERY_TIMEOUT_MS);
    }
}
