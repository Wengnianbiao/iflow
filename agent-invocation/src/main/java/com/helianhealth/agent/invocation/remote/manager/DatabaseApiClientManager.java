package com.helianhealth.agent.invocation.remote.manager;

import com.alibaba.druid.pool.DruidDataSource;
import com.helianhealth.agent.common.base.MetaInfoKeys;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

    private final Map<String, DruidDataSource> dataSourceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Lock> dataSourceLocks = new ConcurrentHashMap<>();

    /**
     * 根据元信息获取数据库连接
     */
    public Connection getConnection(Map<String, Object> metaInfo) throws SQLException {
        String jdbcUrl = (String) metaInfo.get(MetaInfoKeys.URL);
        String username = (String) metaInfo.get(MetaInfoKeys.DB_USERNAME);
        String password = (String) metaInfo.get(MetaInfoKeys.DB_PASSWORD);
        String driverClassName = (String) metaInfo.get(MetaInfoKeys.DB_DRIVER_CLASS_NAME);

        String cacheKey = buildCacheKey(jdbcUrl, username);

        DruidDataSource dataSource = dataSourceCache.get(cacheKey);
        if (dataSource != null) {
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                // 缓存的连接池已失效，清除并关闭，防止Druid后台线程持续重试
                dataSourceCache.remove(cacheKey);
                dataSource.close();
                throw e;
            }
        }

        // 缓存中不存在，创建新的连接池并验证连接
        return createAndCacheDataSource(cacheKey, jdbcUrl, username, password, driverClassName);
    }

    /**
     * 构建缓存 key（URL + 用户名，确保不同账号使用不同连接池）
     */
    private String buildCacheKey(String jdbcUrl, String username) {
        return jdbcUrl + "|" + username;
    }

    /**
     * 创建连接池并验证连接，验证成功才放入缓存
     */
    private Connection createAndCacheDataSource(
            String cacheKey, String jdbcUrl, String username, String password, String driverClassName) throws SQLException {

        Lock lock = dataSourceLocks.computeIfAbsent(cacheKey, k -> new ReentrantLock());
        lock.lock();
        try {
            DruidDataSource existing = dataSourceCache.get(cacheKey);
            if (existing != null) {
                return existing.getConnection();
            }

            // 先用原生JDBC预验证连接
            validateConnection(jdbcUrl, username, password, driverClassName);

            DruidDataSource dataSource = createDataSource(jdbcUrl, username, password, driverClassName);
            try {
                Connection connection = dataSource.getConnection();
                dataSourceCache.put(cacheKey, dataSource);
                return connection;
            } catch (SQLException e) {
                dataSource.close();
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 使用原生JDBC验证数据库连接可用性（单次尝试，失败立即抛出）
     */
    private void validateConnection(String jdbcUrl, String username, String password, String driverClassName) throws SQLException {
        try {
            Class.forName(driverClassName);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Database driver not found: " + driverClassName
                    + ", please check if the driver dependency is correctly configured", e);
        }
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            // 连接成功即验证通过
        } catch (SQLException e) {
            throw new SQLException("Database connection failed, please check the URL, username, and password."
                    + " URL: " + jdbcUrl + ", username: " + username, e);
        }
    }

    /**
     * 创建数据库连接池
     */
    private DruidDataSource createDataSource(String jdbcUrl, String username, String password, String driverClassName) {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);

        // 连接池基础配置
        dataSource.setInitialSize(2);
        dataSource.setMaxActive(20);
        dataSource.setMinIdle(2);
        dataSource.setMaxWait(30000);

        // 连接失败保护配置（凭据已经前置JDBC校验，此处仅针对瞬时故障）
        dataSource.setConnectionErrorRetryAttempts(2);
        dataSource.setBreakAfterAcquireFailure(false);

        // 连接校验配置
        String validationQuery = jdbcUrl.contains("oracle") ? "SELECT 1 FROM DUAL" : "SELECT 1";
        dataSource.setValidationQuery(validationQuery);
        dataSource.setValidationQueryTimeout(5);
        dataSource.setTestWhileIdle(true);
        dataSource.setTestOnBorrow(true);
        dataSource.setTestOnReturn(false);

        // 连接维护配置
        dataSource.setTimeBetweenEvictionRunsMillis(60000);
        dataSource.setMinEvictableIdleTimeMillis(300000);
        dataSource.setPoolPreparedStatements(true);
        dataSource.setMaxPoolPreparedStatementPerConnectionSize(20);

        return dataSource;
    }

    /**
     * 移除指定数据源（配置错误修正后可调用此方法重新创建）
     */
    public void removeDataSource(String jdbcUrl, String username) {
        String cacheKey = buildCacheKey(jdbcUrl, username);
        DruidDataSource dataSource = dataSourceCache.remove(cacheKey);
        if (dataSource != null) {
            dataSource.close();
        }
    }

    /**
     * 关闭连接池（应用关闭时调用）
     */
    public void closeAllDataSources() {
        dataSourceCache.values().forEach(DruidDataSource::close);
        dataSourceCache.clear();
    }
}
