package com.helianhealth.agent.tasks.jdbc.providers;

import com.helianhealth.agent.tasks.jdbc.JdbcTask;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * JDBC 连接工厂：按任务入参每次新建连接，不做池化。
 */
public interface JdbcConnectionProvider {

    Connection openConnection(JdbcTask.Input input) throws SQLException;

    int resolveConnectTimeoutMs(JdbcTask.Input input);

    int resolveQueryTimeoutMs(JdbcTask.Input input);
}
