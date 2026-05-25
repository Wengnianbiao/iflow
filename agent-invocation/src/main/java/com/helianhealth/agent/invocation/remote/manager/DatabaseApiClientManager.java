package com.helianhealth.agent.invocation.remote.manager;

import com.alibaba.druid.pool.DruidDataSource;
import com.helianhealth.agent.common.base.MetaInfoKeyConstants;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 数据库连接管理器（线程安全单例）
 * <p>
 * 超时策略与 {@link HttpApiClientManager} 对齐，分为三层：
 * connectTimeout（建连快速失败）、poolAcquireTimeout（从池借连接）、queryTimeout（SQL 执行/read）。
 * </p>
 */
public class DatabaseApiClientManager {

    /**
     * 建连超时，毫秒。三方库不可达时快速失败。
     */
    private static final int DEFAULT_CONNECT_TIMEOUT = 5000;

    /**
     * Socket/read 超时，毫秒。覆盖真实场景下较慢的 SQL 查询。
     */
    private static final int DEFAULT_QUERY_TIMEOUT = 10000;

    /**
     * 从连接池获取连接的超时，毫秒。对应 HTTP 的 connectionRequestTimeout。
     */
    private static final int DEFAULT_POOL_ACQUIRE_TIMEOUT = 5000;

    /**
     * 借出连接时健康检查（SELECT 1）超时，秒。
     */
    private static final int DEFAULT_VALIDATION_QUERY_TIMEOUT = 3;

    /**
     * SQL 执行超时，秒。供 Statement#setQueryTimeout 使用。
     */
    public static final int DEFAULT_QUERY_TIMEOUT_SECONDS = DEFAULT_QUERY_TIMEOUT / 1000;

    /**
     * 与 WorkFlowNodeExecutor 并发度对齐。
     */
    private static final int DEFAULT_MAX_ACTIVE = 5;

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
        String jdbcUrl = (String) metaInfo.get(MetaInfoKeyConstants.URL);
        String username = (String) metaInfo.get(MetaInfoKeyConstants.DB_USERNAME);
        String password = (String) metaInfo.get(MetaInfoKeyConstants.DB_PASSWORD);
        String driverClassName = (String) metaInfo.get(MetaInfoKeyConstants.DB_DRIVER_CLASS_NAME);

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

            // 先用原生JDBC预验证连接（带 connect 超时，不可用库快速失败）
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
        Properties props = buildConnectionProperties(username, password);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, props)) {
            // 连接成功即验证通过
        } catch (SQLException e) {
            throw new SQLException("Database connection failed, please check the URL, username, and password."
                    + " URL: " + jdbcUrl + ", username: " + username, e);
        }
    }

    /**
     * 构建 JDBC 连接属性，注入各驱动通用的 connect/read 超时参数。
     */
    private Properties buildConnectionProperties(String username, String password) {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        applyTimeoutProperties(props);
        return props;
    }

    /**
     * 注入常见 JDBC 驱动的 connect/read 超时参数。
     */
    private Properties buildDriverTimeoutProperties() {
        Properties props = new Properties();
        applyTimeoutProperties(props);
        return props;
    }

    private void applyTimeoutProperties(Properties props) {
        props.setProperty("connectTimeout", String.valueOf(DEFAULT_CONNECT_TIMEOUT));
        props.setProperty("socketTimeout", String.valueOf(DEFAULT_QUERY_TIMEOUT));
        props.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(DEFAULT_CONNECT_TIMEOUT));
        props.setProperty("oracle.jdbc.ReadTimeout", String.valueOf(DEFAULT_QUERY_TIMEOUT));
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
        dataSource.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
        dataSource.setSocketTimeout(DEFAULT_QUERY_TIMEOUT);
        dataSource.setConnectProperties(buildDriverTimeoutProperties());

        // 连接池基础配置（与 WorkFlowNodeExecutor 并发度对齐）
        dataSource.setInitialSize(1);
        dataSource.setMaxActive(DEFAULT_MAX_ACTIVE);
        dataSource.setMinIdle(0);
        dataSource.setMaxWait(DEFAULT_POOL_ACQUIRE_TIMEOUT);

        // 连接失败保护：凭据已前置校验，连续建连失败时快速熔断，避免后台持续重试
        dataSource.setConnectionErrorRetryAttempts(1);
        dataSource.setBreakAfterAcquireFailure(true);

        // 连接校验配置：借出时做轻量健康检查，超时单独控制且应短于业务 SQL
        String validationQuery = jdbcUrl.contains("oracle") ? "SELECT 1 FROM DUAL" : "SELECT 1";
        dataSource.setValidationQuery(validationQuery);
        dataSource.setValidationQueryTimeout(DEFAULT_VALIDATION_QUERY_TIMEOUT);
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
