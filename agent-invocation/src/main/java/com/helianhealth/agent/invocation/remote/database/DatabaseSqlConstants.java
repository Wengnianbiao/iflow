package com.helianhealth.agent.invocation.remote.database;

/**
 * 数据库SQL操作相关常量
 */
public class DatabaseSqlConstants {

    /**
     * 数据库操作类型
     */
    public static final String OPERATION_INSERT = "INSERT";
    public static final String OPERATION_UPDATE = "UPDATE";
    public static final String OPERATION_DELETE = "DELETE";
    public static final String OPERATION_SELECT = "SELECT";

    /**
     * SQL关键字
     */
    public static final String SQL_KEYWORD_VALUES = "VALUES";
    public static final String SQL_KEYWORD_ON_DUPLICATE_KEY_UPDATE = "ON DUPLICATE KEY UPDATE";
    public static final String SQL_KEYWORD_SELECT = "SELECT";

    /**
     * 响应字段名
     */
    public static final String RESPONSE_FIELD_AFFECTED_ROWS = "affectedRows";
    public static final String RESPONSE_FIELD_DATA = "data";
    public static final String RESPONSE_FIELD_TOTAL = "total";

    /**
     * SQL占位符
     */
    public static final String PLACEHOLDER_START = "{";
    public static final String PLACEHOLDER_END = "}";

    /**
     * NULL值表示
     */
    public static final String SQL_NULL = "NULL";

    private DatabaseSqlConstants() {
        // 私有构造函数,防止实例化
    }
}
