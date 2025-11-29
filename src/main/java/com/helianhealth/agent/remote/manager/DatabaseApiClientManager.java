package com.helianhealth.agent.remote.manager;

import com.alibaba.druid.pool.DruidDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据库连接管理器（线程安全单例）
 */
public class DatabaseApiClientManager {

    private DatabaseApiClientManager() {
    }

    private static volatile DatabaseApiClientManager INSTANCE;

    /**
     * 获取单例实例
     */
    public static DatabaseApiClientManager getInstance() {
        if (INSTANCE == null) {
            synchronized (DatabaseApiClientManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DatabaseApiClientManager();
                }
            }
        }
        return INSTANCE;
    }

    private final Map<String, DruidDataSource> dataSourceCache = new HashMap<>();

    /**
     * 根据元信息获取数据库连接
     */
    public Connection getConnection(Map<String, Object> metaInfo) throws SQLException {
        // 获取或创建连接池
        DruidDataSource dataSource = getOrCreateDataSource(metaInfo);

        // 从连接池获取连接
        return dataSource.getConnection();
    }

    /**
     * 获取或创建数据库连接池
     */
    private synchronized DruidDataSource getOrCreateDataSource(Map<String, Object> metaMap) {
        String jdbcUrl = (String)metaMap.get("url");
        String username = (String)metaMap.get("username");
        String password = (String)metaMap.get("password");
        String driverClassName = (String)metaMap.get("driverClassName");

        return dataSourceCache.computeIfAbsent(jdbcUrl, url -> {
            DruidDataSource dataSource = new DruidDataSource();
            dataSource.setUrl(url);
            dataSource.setUsername(username);
            dataSource.setPassword(password);
            dataSource.setDriverClassName(driverClassName);

            // Druid 连接池默认配置
            dataSource.setInitialSize(5);         // 初始化连接数
            dataSource.setMaxActive(20);          // 最大连接数
            dataSource.setMinIdle(5);             // 最小空闲连接数
            dataSource.setMaxWait(60000);         // 最大等待时间（毫秒）
            dataSource.setTimeBetweenEvictionRunsMillis(60000);
            dataSource.setMinEvictableIdleTimeMillis(300000);
            String validationQuery = url.contains("oracle") ? "SELECT 1 FROM DUAL" : "SELECT 1";
            dataSource.setValidationQuery(validationQuery);
            dataSource.setTestWhileIdle(true);
            dataSource.setTestOnBorrow(false);
            dataSource.setTestOnReturn(false);
            dataSource.setPoolPreparedStatements(true);
            dataSource.setMaxPoolPreparedStatementPerConnectionSize(20);

            return dataSource;
        });
    }

    /**
     * 关闭连接池（应用关闭时调用）
     */
    public void closeAllDataSources() {
        dataSourceCache.values().forEach(DruidDataSource::close);
        dataSourceCache.clear();
    }
}
