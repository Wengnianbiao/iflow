package com.helianhealth.agent.tasks.jdbc.providers;

import com.helianhealth.agent.tasks.jdbc.JdbcConnectionUtils;
import com.helianhealth.agent.tasks.jdbc.JdbcTask;
import com.helianhealth.agent.tasks.jdbc.JdbcVendor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;

/**
 * 默认 {@link JdbcConnectionProvider}：按 {@link JdbcTask.Input} 中的 URL、账号、厂商/驱动
 * 及超时参数，每次调用通过 {@link DriverManager} 新建连接。
 */
@Component
public class DefaultJdbcConnectionProvider implements JdbcConnectionProvider {

    private final int defaultConnectTimeoutMs;
    private final int defaultQueryTimeoutMs;

    public DefaultJdbcConnectionProvider(
            @Value("${iflow.tasks.jdbc.connectTimeout:3000ms}") Duration connectTimeout,
            @Value("${iflow.tasks.jdbc.queryTimeout:20000ms}") Duration queryTimeout) {
        this.defaultConnectTimeoutMs = (int) connectTimeout.toMillis();
        this.defaultQueryTimeoutMs = (int) queryTimeout.toMillis();
    }

    public DefaultJdbcConnectionProvider(int defaultConnectTimeoutMs, int defaultQueryTimeoutMs) {
        this.defaultConnectTimeoutMs = defaultConnectTimeoutMs;
        this.defaultQueryTimeoutMs = defaultQueryTimeoutMs;
    }

    @Override
    public @NonNull Connection openConnection(@NonNull JdbcTask.Input input) throws SQLException {
        String driverClassName = JdbcVendor.resolveDriverClassName(input.getDriverClassName(), input.getVendor());
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
        return Optional.ofNullable(input.getConnectionTimeOut()).orElse(defaultConnectTimeoutMs);
    }

    @Override
    public int resolveQueryTimeoutMs(JdbcTask.Input input) {
        return Optional.ofNullable(input.getQueryTimeOut()).orElse(defaultQueryTimeoutMs);
    }
}
