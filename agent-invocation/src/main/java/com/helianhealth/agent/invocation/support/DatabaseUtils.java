package com.helianhealth.agent.invocation.support;

import com.helianhealth.agent.invocation.remote.manager.DatabaseApiClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseUtils {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseUtils.class);

    private static final DatabaseApiClientManager databaseApiClientManager = DatabaseApiClientManager.getInstance();

    private static Connection getConnection(Map<String, Object> metaInfo) throws SQLException {
        return databaseApiClientManager.getConnection(metaInfo);
    }

    /**
     * 直接执行SQL查询语句
     * @param metaInfo 数据库连接信息
     * @param sql 完整的SQL查询语句
     * @return 查询结果集
     * @throws SQLException SQL异常
     */
    public static List<Map<String, Object>> executeQuery(Map<String, Object> metaInfo, String sql) throws SQLException {
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        try {
            connection = getConnection(metaInfo);
            statement = connection.createStatement();

            resultSet = statement.executeQuery(sql);
            return resultSetToList(resultSet);
        } finally {
            closeResources(resultSet, statement, connection);
        }
    }


    /**
     * 将ResultSet转换为List<Map<String, Object>>
     * @param resultSet 查询结果集
     * @return 结果列表
     * @throws SQLException SQL异常
     */
    private static List<Map<String, Object>> resultSetToList(ResultSet resultSet) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();

        while (resultSet.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(metaData.getColumnLabel(i), resultSet.getObject(i));
            }
            result.add(row);
        }

        return result;
    }

    /**
     * 直接执行SQL更新语句
     * @param metaInfo 数据库连接信息
     * @param sql 完整的SQL更新语句
     * @return 影响的行数
     * @throws SQLException SQL异常
     */
    public static int executeUpdate(Map<String, Object> metaInfo, String sql) throws SQLException {
        Connection connection = null;
        Statement statement = null;
        try {
            connection = getConnection(metaInfo);
            statement = connection.createStatement();

            logger.info("Executing direct update: {}", sql);
            return statement.executeUpdate(sql);
        } finally {
            closeResources(null, statement, connection);
        }
    }


    /**
     * 执行INSERT语句
     * @param metaInfo 数据库连接信息
     * @param sql INSERT SQL语句
     * @return 影响的行数
     * @throws SQLException SQL异常
     */
    public static int executeInsert(Map<String, Object> metaInfo, String sql) throws SQLException {
        return executeUpdate(metaInfo, sql);
    }

    /**
     * 执行DELETE语句
     * @param metaInfo 数据库连接信息
     * @param sql DELETE SQL语句
     * @return 影响的行数
     * @throws SQLException SQL异常
     */
    public static int executeDelete(Map<String, Object> metaInfo, String sql) throws SQLException {
        return executeUpdate(metaInfo, sql);
    }

    /**
     * 关闭数据库资源
     * @param resultSet 结果集
     * @param statement Statement对象
     * @param connection 连接对象
     */
    private static void closeResources(ResultSet resultSet, Statement statement, Connection connection) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                logger.warn("Failed to close ResultSet", e);
            }
        }

        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                logger.warn("Failed to close Statement", e);
            }
        }

        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.warn("Failed to close Connection", e);
            }
        }
    }
}
