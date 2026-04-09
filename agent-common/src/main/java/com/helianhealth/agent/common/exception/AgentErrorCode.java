package com.helianhealth.agent.common.exception;

import lombok.Getter;

/**
 * 框架统一错误码枚举
 * <p>
 * 错误码分段规则：
 * 1xxx - 配置类错误（工作流配置、参数配置、表达式配置等）
 * 2xxx - 调用类错误（数据库调用、HTTP调用、WebService调用等）
 * 8xxx - 控制台操作错误
 * 9xxx - 框架内部错误（JSON处理、类型转换等）
 */
@Getter
public enum AgentErrorCode {

    // ---- 配置类错误 (1xxx) ----

    // 工作流配置 (11xx)
    WORKFLOW_NOT_FOUND(1101, "工作流不存在"),
    NODE_NOT_FOUND(1102, "工作流节点不存在"),
    METHOD_NOT_SUPPORT(1103, "不支持的方法调用"),
    NODE_TYPE_UNSUPPORTED(1104, "不支持的节点类型"),
    MOCK_CONFIG_INVALID(1105, "Mock配置校验失败"),

    // 表达式配置 (12xx)
    FILTER_EXPRESSION_PARSE_ERROR(1201, "参数过滤表达式解析失败"),
    SCHEDULE_EXPRESSION_PARSE_ERROR(1202, "调度表达式解析失败"),

    // 参数与协议配置 (13xx)
    PRE_PARAM_TREE_BUILD_ERROR(1301, "前置参数树构建失败"),
    POST_PARAM_TREE_BUILD_ERROR(1302, "后置参数树构建失败"),
    BUSINESS_DATA_SPLIT_ERROR(1303, "业务数据拆分异常"),
    CONTENT_TYPE_ADAPT_ERROR(1304, "请求协议适配失败"),
    CONTENT_TYPE_UNSUPPORTED(1305, "不支持的内容类型"),

    // SQL配置 (14xx)
    SQL_TEMPLATE_MISSING(1401, "SQL模式下务必有模板配置"),
    SQL_OPERATION_MISSING(1402, "SQL模式下务必有操作类型"),
    SQL_PARAM_TYPE_ERROR(1403, "SQL模式下节点类型错误"),
    SQL_PLACEHOLDER_UNMATCHED(1404, "SQL模板中存在未匹配的参数占位符"),
    SQL_ARRAY_CHILD_SIZE_ERROR(1405, "SQL模式SELECT操作数组仅支持单个元素配置"),
    SQL_UNSUPPORTED_PARAM_TYPE(1406, "Insert语句不支持的参数类型"),

    // ---- 调用类错误 (2xxx) ----

    // 数据库调用 (21xx)
    DB_CONNECTION_FAILED(2101, "数据库连接失败，请核对用户名和密码"),
    DB_EXECUTION_FAILED(2102, "SQL执行失败"),

    // HTTP调用 (22xx)
    HTTP_CALL_FAILED(2201, "HTTP调用失败"),
    HTTP_CONFIG_MISSING(2202, "HTTP请求配置缺失"),

    // WebService调用 (23xx)
    WS_CALL_FAILED(2301, "WebService调用失败"),
    WS_MESSAGE_BUILD_ERROR(2302, "SOAP消息构建失败"),
    WS_RESPONSE_PARSE_ERROR(2303, "SOAP响应解析失败"),
    WS_CONFIG_MISSING(2304, "WebService请求配置缺失"),

    // 远程响应 (29xx)
    REMOTE_RESPONSE_PARSE_ERROR(2901, "远程响应解析失败"),

    // ---- 控制台操作错误 (8xxx) ----
    CONSOLE_OPERATION_FAILED(8001, "控制台操作失败"),

    // ---- 框架内部错误 (9xxx) ----

    // JSON处理 (91xx)
    JSON_SERIALIZE_ERROR(9101, "JSON序列化失败"),
    JSON_PARSE_ERROR(9102, "JSON解析失败"),
    JSON_DEEP_COPY_ERROR(9103, "对象深拷贝失败"),

    // 类型转换 (92xx)
    TYPE_CONVERSION_ERROR(9201, "参数类型转换失败"),

    // 通用内部错误 (99xx)
    INTERNAL_ERROR(9999, "框架内部错误");

    private final int code;
    private final String message;

    AgentErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
