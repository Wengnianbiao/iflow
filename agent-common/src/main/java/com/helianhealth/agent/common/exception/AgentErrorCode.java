package com.helianhealth.agent.common.exception;

/**
 * 框架统一错误码枚举
 * <p>
 * 错误码分段规则：
 * 1xxxx - 配置类错误（工作流配置、参数配置、表达式配置等）
 * 2xxxx - 调用类错误（数据库调用、HTTP调用、WebService调用等）
 * 8xxxx - 控制台操作错误
 * 9xxxx - 框架内部错误（JSON处理、类型转换等）
 */
public enum AgentErrorCode {

    // ---- 配置类错误 (1xxxx) ----

    // 工作流配置 (11xxx)
    WORKFLOW_NOT_FOUND(11001, "工作流不存在"),
    NODE_NOT_FOUND(11002, "工作流节点不存在"),
    METHOD_NOT_SUPPORT(11003, "不支持的方法调用"),
    NODE_TYPE_UNSUPPORTED(11004, "不支持的节点类型"),
    MOCK_CONFIG_INVALID(11005, "Mock配置校验失败"),

    // 表达式配置 (12xxx)
    FILTER_EXPRESSION_PARSE_ERROR(12001, "参数过滤表达式解析失败"),
    SCHEDULE_EXPRESSION_PARSE_ERROR(12002, "调度表达式解析失败"),

    // 参数与协议配置 (13xxx)
    PRE_PARAM_TREE_BUILD_ERROR(13001, "前置参数树构建失败"),
    POST_PARAM_TREE_BUILD_ERROR(13002, "后置参数树构建失败"),
    BUSINESS_DATA_SPLIT_ERROR(13003, "业务数据拆分异常"),
    CONTENT_TYPE_ADAPT_ERROR(13004, "请求协议适配失败"),
    CONTENT_TYPE_UNSUPPORTED(13005, "不支持的内容类型"),

    // SQL配置 (14xxx)
    SQL_TEMPLATE_MISSING(14001, "SQL模式下务必有模板配置"),
    SQL_OPERATION_MISSING(14002, "SQL模式下务必有操作类型"),
    SQL_PARAM_TYPE_ERROR(14003, "SQL模式下节点类型错误"),
    SQL_PLACEHOLDER_UNMATCHED(14004, "SQL模板中存在未匹配的参数占位符"),
    SQL_ARRAY_CHILD_SIZE_ERROR(14005, "SQL模式SELECT操作数组仅支持单个元素配置"),
    SQL_UNSUPPORTED_PARAM_TYPE(14006, "Insert语句不支持的参数类型"),

    // ---- 调用类错误 (2xxxx) ----

    // 数据库调用 (21xxx)
    DB_CONNECTION_FAILED(21001, "数据库连接失败，请核对用户名和密码"),
    DB_EXECUTION_FAILED(21002, "SQL执行失败"),

    // HTTP调用 (22xxx)
    HTTP_CALL_FAILED(22001, "HTTP调用失败"),
    HTTP_CONFIG_MISSING(22002, "HTTP请求配置缺失"),

    // WebService调用 (23xxx)
    WS_CALL_FAILED(23001, "WebService调用失败"),
    WS_MESSAGE_BUILD_ERROR(23002, "SOAP消息构建失败"),
    WS_RESPONSE_PARSE_ERROR(23003, "SOAP响应解析失败"),
    WS_CONFIG_MISSING(23004, "WebService请求配置缺失"),

    // 远程响应 (29xxx)
    REMOTE_RESPONSE_PARSE_ERROR(29001, "远程响应解析失败"),

    // ---- 控制台操作错误 (8xxxx) ----
    CONSOLE_OPERATION_FAILED(80001, "控制台操作失败"),
    INVALID_NODE_ID_LIST(80002, "无效的节点ID列表"),
    UNSUPPORTED_EXTENSION_TYPE(80003, "不支持的扩展类型"),
    EXTENSION_NAME_DUPLICATE(80004, "扩展名称已存在"),
    SPI_REGISTER_FAILED(80005, "SPI注册失败"),

    // ---- 框架内部错误 (9xxxx) ----

    // JSON处理 (91xxx)
    JSON_SERIALIZE_ERROR(91001, "JSON序列化失败"),
    JSON_PARSE_ERROR(91002, "JSON解析失败"),
    JSON_DEEP_COPY_ERROR(91003, "对象深拷贝失败"),

    // 类型转换 (92xxx)
    TYPE_CONVERSION_ERROR(92001, "参数类型转换失败"),

    // 通用内部错误 (99xxx)
    INTERNAL_ERROR(99999, "框架内部错误");

    private final int code;
    private final String message;

    AgentErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
