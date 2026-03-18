package com.helianhealth.agent.console.controller.request;

import lombok.Data;

/**
 * SQL执行请求
 */
@Data
public class SqlExecuteRequest {
    
    /**
     * 数据库类型 (mysql/postgresql/oracle/sqlserver/dm/kingbase)
     */
    private String dbType;
    
    /**
     * JDBC连接URL
     */
    private String url;
    
    /**
     * 数据库用户名
     */
    private String username;
    
    /**
     * 数据库密码
     */
    private String password;
    
    /**
     * SQL语句（仅支持SELECT）
     */
    private String sql;
    
    /**
     * 最大返回行数
     */
    private Integer maxRows;
}
