package com.helianhealth.agent.common.base;

public class XmlConstants {

    /**
     * 外层标签名 - 用于定位 CDATA 包裹的业务数据位置
     */
    public static final String ELEMENT_NAME_KEY = "elementName";

    /**
     * 内层标签名 - 用于在 CDATA 内容中进一步定位真正的业务数据
     * 适用于 CDATA 内容包含 XML 声明（如 <?xml version="1.0"?>）的场景
     */
    public static final String INNER_ELEMENT_NAME_KEY = "innerElementName";

    public static final String DATA_TYPE_KEY = "dataType";

    public static final String JSON_KEY = "json";

    public static final String IS_CDATA_KEY = "isCDATA";

    /**
     * 是否使用转义符替换符串中的特殊字符
     */
    public static final String USE_ESCAPE_KEY = "escape";

    /**
     * 对象标签 - 指定哪些父元素应保留对象结构（其子元素不转为数组）
     * 用于覆盖默认的"单一子标签名=数组"规则
     */
    public static final String OBJECT_TAG_KEY = "objectTag";
}
