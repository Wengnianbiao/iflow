package com.helianhealth.iflow.tasks.jdbc;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * JDBC 任务支持的厂商与驱动类名，驱动 JAR 由本模块的运行时依赖提供。
 */
public enum JdbcVendor {

    MYSQL("com.mysql.cj.jdbc.Driver"),
    POSTGRES("org.postgresql.Driver"),
    SQLSERVER("com.microsoft.sqlserver.jdbc.SQLServerDriver"),
    ORACLE("oracle.jdbc.OracleDriver"),
    OPENGAUSS("org.opengauss.Driver"),
    KINGBASE("com.kingbase8.Driver");

    private final String driverClassName;

    JdbcVendor(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    @JsonCreator
    public static JdbcVendor from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return JdbcVendor.valueOf(value.trim().toUpperCase());
    }

    public static String resolveDriverClassName(JdbcVendor vendor) {
        if (vendor == null) {
            throw new IllegalArgumentException("missing vendor");
        }
        return vendor.getDriverClassName();
    }
}
