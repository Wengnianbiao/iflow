package com.helianhealth.agent.console.service;

import com.helianhealth.agent.console.controller.request.SqlExecuteRequest;
import com.helianhealth.agent.console.controller.reponse.SqlExecuteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.*;
import java.util.*;

/**
 * SQL执行服务
 * 用于执行SELECT查询语句
 */
@Service
@Slf4j
public class SqlExecuteService {

    /**
     * 执行SELECT查询
     */
    public SqlExecuteResponse executeQuery(SqlExecuteRequest request) {
        // 参数校验
        validateRequest(request);
        
        // 验证SQL是否为SELECT语句
        String sql = request.getSql().trim();
        if (!sql.toUpperCase().startsWith("SELECT")) {
            throw new IllegalArgumentException("仅支持SELECT查询语句");
        }
        
        int maxRows = request.getMaxRows() != null ? request.getMaxRows() : 100;
        
        log.info("执行SQL查询: url={}, sql={}", request.getUrl(), sql);
        
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        
        try {
            // 加载驱动
            loadDriver(request.getDbType());
            
            // 获取连接
            conn = DriverManager.getConnection(
                request.getUrl(),
                request.getUsername(),
                request.getPassword()
            );
            
            // 创建Statement并设置最大行数
            stmt = conn.createStatement();
            stmt.setMaxRows(maxRows);
            stmt.setQueryTimeout(60); // 60秒超时
            
            // 执行查询
            rs = stmt.executeQuery(sql);
            
            // 解析结果
            return parseResultSet(rs);
            
        } catch (SQLException e) {
            log.error("SQL执行失败: {}", e.getMessage(), e);
            throw new RuntimeException("SQL执行失败: " + e.getMessage(), e);
        } finally {
            // 关闭资源
            closeQuietly(rs);
            closeQuietly(stmt);
            closeQuietly(conn);
        }
    }
    
    private void validateRequest(SqlExecuteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (!StringUtils.hasText(request.getUrl())) {
            throw new IllegalArgumentException("数据库连接URL不能为空");
        }
        if (!StringUtils.hasText(request.getSql())) {
            throw new IllegalArgumentException("SQL语句不能为空");
        }
    }
    
    private void loadDriver(String dbType) {
        if (!StringUtils.hasText(dbType)) {
            dbType = "mysql";
        }
        
        String driverClass;
        switch (dbType.toLowerCase()) {
            case "mysql":
                driverClass = "com.mysql.cj.jdbc.Driver";
                break;
            case "postgresql":
                driverClass = "org.postgresql.Driver";
                break;
            case "oracle":
                driverClass = "oracle.jdbc.OracleDriver";
                break;
            case "sqlserver":
                driverClass = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
                break;
            case "dm":
                driverClass = "dm.jdbc.driver.DmDriver";
                break;
            case "kingbase":
                driverClass = "com.kingbase8.Driver";
                break;
            default:
                driverClass = "com.mysql.cj.jdbc.Driver";
        }
        
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            log.warn("数据库驱动加载失败: {}，尝试继续执行", driverClass);
            // 不抛异常，让DriverManager自动识别
        }
    }
    
    private SqlExecuteResponse parseResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        // 获取列名
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            columns.add(metaData.getColumnLabel(i));
        }
        
        // 获取数据行
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = columns.get(i - 1);
                Object value = rs.getObject(i);
                // 处理特殊类型
                if (value instanceof java.sql.Timestamp) {
                    value = value.toString();
                } else if (value instanceof java.sql.Date) {
                    value = value.toString();
                } else if (value instanceof java.sql.Time) {
                    value = value.toString();
                } else if (value instanceof byte[]) {
                    value = "[BLOB]";
                } else if (value instanceof java.sql.Clob) {
                    value = "[CLOB]";
                } else if (value instanceof java.sql.Blob) {
                    value = "[BLOB]";
                }
                row.put(columnName, value);
            }
            rows.add(row);
        }
        
        log.info("SQL查询完成，返回{}列，{}行", columns.size(), rows.size());
        
        return SqlExecuteResponse.builder()
            .columns(columns)
            .rows(rows)
            .build();
    }
    
    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn("关闭资源失败", e);
            }
        }
    }
}
