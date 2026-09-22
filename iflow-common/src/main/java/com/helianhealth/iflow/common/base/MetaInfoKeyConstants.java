package com.helianhealth.iflow.common.base;

/**
 * MetaInfo配置项的Key常量
 * <p>
 * 用于统一管理各协议节点(HTTP、WebService、Database等)metaInfo JSON中的配置项key
 * </p>
 */
public class MetaInfoKeyConstants {

    // ==================== 公共配置项 ====================

    /**
     * 接口地址/连接串
     * - HTTP: HTTP请求URL
     * - WebService: SOAP请求URL
     * - Database: JDBC连接串
     */
    public static final String URL = "url";

    /**
     * 响应格式: json/xml
     */
    public static final String RESPONSE_TYPE = "responseType";

    /**
     * 请求头配置(Map结构)
     */
    public static final String HEADERS = "headers";


    // ==================== HTTP协议专用 ====================

    /**
     * HTTP请求方法: GET/POST/PUT/DELETE等
     */
    public static final String HTTP_METHOD = "method";


    // ==================== WebService协议专用 ====================

    /**
     * SOAP请求模板
     */
    public static final String SOAP_TEMPLATE = "soupTemplate";

    /**
     * 数据类型: json/xml
     */
    public static final String DATA_TYPE = "dataType";

    /**
     * JSON数据类型标识值
     */
    public static final String DATA_TYPE_JSON = "json";

    /**
     * XML数据类型标识值
     */
    public static final String DATA_TYPE_XML = "xml";

    /**
     * 是否使用转义符替换特殊字符
     */
    public static final String USE_ESCAPE = "escape";

    /**
     * 外层标签名 - 用于定位CDATA包裹的业务数据位置
     */
    public static final String ELEMENT_NAME = "elementName";

    /**
     * 内层标签名 - 用于在CDATA内容中进一步定位真正的业务数据
     */
    public static final String INNER_ELEMENT_NAME = "innerElementName";

    /**
     * 是否使用兼容型 SOAP 发送通道（基于 HttpURLConnection）
     */
    public static final String SOAP_USE_COMPATIBLE_SENDER_KEY = "soapUseCompatibleSender";

    /**
     * 是否使用CDATA包裹
     */
    public static final String IS_CDATA = "isCDATA";

    /**
     * 对象标签 - 指定哪些父元素应保留对象结构
     */
    public static final String OBJECT_TAG = "objectTag";

    /**
     * 是否启用兼容型 SOAP 发送通道（基于 HttpURLConnection）
     */
    public static final String SOAP_USE_COMPATIBLE_SENDER = "soapUseCompatibleSender";


    // ==================== Database协议专用 ====================

    /**
     * 数据库操作类型: SELECT/INSERT/UPDATE/DELETE
     */
    public static final String DB_OPERATION = "operation";

    /**
     * 数据库用户名
     */
    public static final String DB_USERNAME = "username";

    /**
     * 数据库密码
     */
    public static final String DB_PASSWORD = "password";

    /**
     * SQL模板
     */
    public static final String DB_SQL_TEMPLATE = "sqlTemplate";


    private MetaInfoKeyConstants() {
        // 私有构造函数,防止实例化
    }
}
