package com.helianhealth.agent.common.utils;

import com.helianhealth.agent.common.base.XmlConstants;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XmlUtils.parseXmlForConfig 方法测试用例
 */
public class XmlUtilsParseXmlForConfigTest {

    // ==================== 1. 基础解析场景 ====================

    @Test
    public void testSimpleXml_NoConfig() {
        // 简单 XML，无任何配置 - 只有一种子标签名，识别为数组
        String xml = "<Response><patientCode>123456</patientCode></Response>";
        Map<String, Object> metaMap = new HashMap<>();

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // Response 下只有 patientCode 一种标签 → 数组
        assertTrue(result.get("patientCode") instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) result.get("patientCode");
        assertEquals("123456", list.get(0));
    }

    @Test
    public void testNestedXml_NoConfig() {
        // 嵌套 XML - patient 下有多种子元素，不是数组
        String xml = "<Response><patient><code>P001</code><name>张三</name></patient></Response>";
        Map<String, Object> metaMap = new HashMap<>();

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // Response 下只有 patient 一种标签 → patient 是数组
        // patient 下有 code 和 name 两种标签 → 它们是对象属性
        assertInstanceOf(List.class, result.get("patient"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> patients = (List<Map<String, Object>>) result.get("patient");
        assertEquals("P001", patients.get(0).get("code"));
        assertEquals("张三", patients.get(0).get("name"));
    }

    @Test
    public void testMultiLevelNestedXml() {
        // 多层嵌套 - 每层只有一种子标签，都是数组
        String xml = "<Root><Level1><Level2><value>深层数据</value></Level2></Level1></Root>";
        Map<String, Object> metaMap = new HashMap<>();

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // Root→Level1(数组)→Level2(数组)→value(数组)
        assertInstanceOf(List.class, result.get("Level1"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> level1 = (List<Map<String, Object>>) result.get("Level1");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> level2 = (List<Map<String, Object>>) level1.get(0).get("Level2");
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) level2.get(0).get("value");
        assertEquals("深层数据", values.get(0));
    }

    @Test
    public void testMultipleChildTypes_NoSimplify() {
        // 多种子元素类型 - 不简化
        String xml = "<Response><code>001</code><name>测试</name></Response>";
        Map<String, Object> metaMap = new HashMap<>();

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        assertEquals("001", result.get("code"));
        assertEquals("测试", result.get("name"));
    }

    // ==================== 2. elementName 外层标签定位 ====================

    @Test
    public void testElementName_CdataXml() {
        // 使用 elementName 定位 CDATA 包裹的内容
        String xml = "<Body><Result><![CDATA[<patient><code>P001</code></patient>]]></Result></Body>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(XmlConstants.ELEMENT_NAME_KEY, "Result");

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // patient 下只有 code 一种标签 → 数组
        assertInstanceOf(List.class, result.get("code"));
        @SuppressWarnings("unchecked")
        List<Object> codes = (List<Object>) result.get("code");
        assertEquals("P001", codes.get(0));
    }

    @Test
    public void testElementName_WithXmlDeclaration() {
        // CDATA 内容包含 XML 声明
        String xml = "<Body><Result><![CDATA[<?xml version=\"1.0\" encoding=\"UTF-8\"?><data><id>001</id></data>]]></Result></Body>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(XmlConstants.ELEMENT_NAME_KEY, "Result");

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // data 下只有 id 一种标签 → 数组
        assertInstanceOf(List.class, result.get("id"));
        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) result.get("id");
        assertEquals("001", ids.get(0));
    }

    @Test
    public void testElementName_NotFound() {
        // elementName 对应的标签不存在
        String xml = "<Response><data>test</data></Response>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(XmlConstants.ELEMENT_NAME_KEY, "NotExist");

        assertThrows(RuntimeException.class, () -> XmlUtils.parseResponseXml(xml, metaMap));
    }

    @Test
    public void testElementName_MultipleNodes() {
        // elementName 对应多个节点
        String xml = "<Root><Item>1</Item><Item>2</Item></Root>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(XmlConstants.ELEMENT_NAME_KEY, "Item");

        assertThrows(RuntimeException.class, () -> XmlUtils.parseResponseXml(xml, metaMap));
    }

    // ==================== 3. innerElementName 内层二级定位 ====================

    @Test
    public void testInnerElementName_Locate() {
        // 使用 innerElementName 进一步定位
        String xml = "<Body><Result><![CDATA[<root><wrapper><data><id>002</id></data></wrapper></root>]]></Result></Body>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(XmlConstants.ELEMENT_NAME_KEY, "Result");
        metaMap.put(XmlConstants.INNER_ELEMENT_NAME_KEY, "data");

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // data 下只有 id 一种标签 → 数组
        assertInstanceOf(List.class, result.get("id"));
        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) result.get("id");
        assertEquals("002", ids.get(0));
    }

    @Test
    public void testInnerElementName_NotFound_FallbackToRoot() {
        // innerElementName 不存在，降级到根元素
        String xml = "<Body><Result><![CDATA[<data><id>003</id></data>]]></Result></Body>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(XmlConstants.ELEMENT_NAME_KEY, "Result");
        metaMap.put(XmlConstants.INNER_ELEMENT_NAME_KEY, "notExist");

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // 降级到根元素 <data>，data 下只有 id 一种标签 → 数组
        assertTrue(result.get("id") instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) result.get("id");
        assertEquals("003", ids.get(0));
    }

    // ==================== 4. isCDATA 配置场景 ====================

    @Test
    public void testIsCDATA_False() {
        // isCDATA=false，直接取子元素
        String xml = "<Body><Result><patient><code>P002</code></patient></Result></Body>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(XmlConstants.ELEMENT_NAME_KEY, "Result");
        metaMap.put(XmlConstants.IS_CDATA_KEY, "false");

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // patient 下只有 code 一种标签 → 数组
        assertTrue(result.get("code") instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> codes = (List<Object>) result.get("code");
        assertEquals("P002", codes.get(0));
    }

    @Test
    public void testIsCDATA_Default_True() {
        // 默认 isCDATA=true
        String xml = "<Body><Result><![CDATA[<patient><code>P003</code></patient>]]></Result></Body>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(XmlConstants.ELEMENT_NAME_KEY, "Result");

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // patient 下只有 code 一种标签 → 数组
        assertTrue(result.get("code") instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> codes = (List<Object>) result.get("code");
        assertEquals("P003", codes.get(0));
    }

    // ==================== 5. dataType=json 配置场景 ====================

    @Test
    public void testDataType_Json() {
        // CDATA 内容是 JSON 格式
        String xml = "<Body><Result><![CDATA[{\"code\":\"J001\",\"name\":\"测试\"}]]></Result></Body>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(XmlConstants.ELEMENT_NAME_KEY, "Result");
        metaMap.put(XmlConstants.DATA_TYPE_KEY, "json");

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        assertEquals("J001", result.get("code"));
        assertEquals("测试", result.get("name"));
    }

    @Test
    public void testDataType_JsonArray() {
        // CDATA 内容是 JSON 数组
        String xml = "<Body><Result><![CDATA[{\"items\":[{\"id\":1},{\"id\":2}]}]]></Result></Body>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(XmlConstants.ELEMENT_NAME_KEY, "Result");
        metaMap.put(XmlConstants.DATA_TYPE_KEY, "JSON");

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        assertNotNull(result.get("items"));
        assertTrue(result.get("items") instanceof List);
    }

    // ==================== 6. escape 转义处理场景 ====================

    @Test
    public void testEscape_Enabled() {
        // 转义字符处理
        String xml = "&lt;Response&gt;&lt;data&gt;test&lt;/data&gt;&lt;/Response&gt;";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(XmlConstants.USE_ESCAPE_KEY, "true");

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // Response 下只有 data 一种标签 → 数组
        assertTrue(result.get("data") instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> dataList = (List<Object>) result.get("data");
        assertEquals("test", dataList.get(0));
    }

    // ==================== 7. 数组场景 ====================

    @Test
    public void testSameNameElements_AutoArray() {
        // 同名子元素自动转为数组
        String xml = "<Root><item>A</item><item>B</item><item>C</item></Root>";
        Map<String, Object> metaMap = new HashMap<>();

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // 不简化数组，返回 {"item": [...]}
        assertTrue(result.get("item") instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) result.get("item");
        assertEquals(3, items.size());
    }

    @Test
    public void testMixedSingleAndMultiple() {
        // 混合单个和多个元素
        String xml = "<Root><name>Test</name><item>A</item><item>B</item></Root>";
        Map<String, Object> metaMap = new HashMap<>();

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        assertEquals("Test", result.get("name"));
        assertTrue(result.get("item") instanceof List);
    }

    // ==================== 8. 属性场景 ====================

    @Test
    public void testElementWithAttributes() {
        // 带属性的元素 - 有属性时不简化，但子元素遵循数组规则
        String xml = "<Root><patient id=\"P001\" type=\"VIP\"><name>张三</name></patient></Root>";
        Map<String, Object> metaMap = new HashMap<>();

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // Root 下只有 patient 一种标签 → patient 是数组
        assertTrue(result.get("patient") instanceof List);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> patients = (List<Map<String, Object>>) result.get("patient");
        Map<String, Object> patient = patients.get(0);
        assertEquals("P001", patient.get("id"));
        assertEquals("VIP", patient.get("type"));
        // patient 有属性，name 是唯一子元素 → name 是数组
        assertTrue(patient.get("name") instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> names = (List<Object>) patient.get("name");
        assertEquals("张三", names.get(0));
    }

    @Test
    public void testRootWithAttributes() {
        // 根元素有属性 - 不简化，子元素遵循数组规则
        String xml = "<Response status=\"ok\"><code>001</code></Response>";
        Map<String, Object> metaMap = new HashMap<>();

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        assertEquals("ok", result.get("status"));
        // Response 有属性，code 是唯一子元素 → code 是数组
        assertTrue(result.get("code") instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> codes = (List<Object>) result.get("code");
        assertEquals("001", codes.get(0));
    }

    // ==================== 9. 空标签场景 ====================

    @Test
    public void testEmptyElement() {
        // 空标签 - 空标签的值为 null
        String xml = "<Root><empty/><data>value</data></Root>";
        Map<String, Object> metaMap = new HashMap<>();

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // empty 是空标签，值为 null；多种子元素类型不简化
        assertTrue(result.containsKey("empty"));
        assertNull(result.get("empty"));
        assertEquals("value", result.get("data"));
    }

    @Test
    public void testSelfClosingTag() {
        // 自闭合标签 - 值为 null，但遵循数组规则
        String xml = "<Response><status/></Response>";
        Map<String, Object> metaMap = new HashMap<>();

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // Response 下只有 status 一种标签 → status 是数组
        assertTrue(result.containsKey("status"));
        assertTrue(result.get("status") instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> statusList = (List<Object>) result.get("status");
        assertNull(statusList.get(0));
    }

    // ==================== 10. 边界/异常场景 ====================

    @Test
    public void testXmlWithWhitespace() {
        // XML 包含换行和空格 - 多种子元素不简化
        String xml = "<Response>\n  <code>001</code>\n  <name>测试</name>\n</Response>";
        Map<String, Object> metaMap = new HashMap<>();

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        assertEquals("001", result.get("code"));
        assertEquals("测试", result.get("name"));
    }

    @Test
    public void testInvalidXml() {
        // 格式错误的 XML
        String xml = "<Root><unclosed>";
        Map<String, Object> metaMap = new HashMap<>();

        assertThrows(RuntimeException.class, () -> XmlUtils.parseResponseXml(xml, metaMap));
    }

    @Test
    public void testEmptyMetaMap() {
        // metaMap 为空
        String xml = "<Response><code>001</code><name>test</name></Response>";
        Map<String, Object> metaMap = new HashMap<>();

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        assertEquals("001", result.get("code"));
        assertEquals("test", result.get("name"));
    }

    // ==================== 11. 实际业务场景 ====================

    @Test
    public void testRealSoapResponse() {
        // 模拟真实 SOAP 响应
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Envelope>" +
                "<Body>" +
                "<GetPatientResponse>" +
                "<Result><![CDATA[<patient><code>P12345</code><name>李四</name><age>30</age></patient>]]></Result>" +
                "</GetPatientResponse>" +
                "</Body>" +
                "</Envelope>";

        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(XmlConstants.ELEMENT_NAME_KEY, "Result");

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        assertEquals("P12345", result.get("code"));
        assertEquals("李四", result.get("name"));
        assertEquals("30", result.get("age"));
    }

    @Test
    public void testRealSoapResponse_WithInnerElement() {
        // 模拟真实 SOAP 响应 + 内层定位
        String xml = "<Body>" +
                "<Result><![CDATA[" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<response><status>success</status><data><id>1</id><name>test</name></data></response>" +
                "]]></Result>" +
                "</Body>";

        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(XmlConstants.ELEMENT_NAME_KEY, "Result");
        metaMap.put(XmlConstants.INNER_ELEMENT_NAME_KEY, "data");

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        assertEquals("1", result.get("id"));
        assertEquals("test", result.get("name"));
    }

    @Test
    public void testChineseContent() {
        // 中文内容 - 多种子元素不简化
        String xml = "<响应><姓名>王五</姓名><诊断>感冒</诊断></响应>";
        Map<String, Object> metaMap = new HashMap<>();

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        assertEquals("王五", result.get("姓名"));
        assertEquals("感冒", result.get("诊断"));
    }

    @Test
    public void testObjectTagConfig() {
        // objectTag 配置 - 暂时保留用于未来扩展
        String xml = "<Root><items><item><id>1</id></item></items></Root>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(XmlConstants.OBJECT_TAG_KEY, Collections.singletonList("items"));

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // items 只有一个子元素，会被简化
        assertNotNull(result);
    }

    @Test
    public void testItemHisID_Scenario() {
        // 用户的真实场景：ItemHisID 下只有 HIS_ID，应简化为数组
        String xml = "<Request>" +
                "<OPERATOR_TIME>2026-01-29 12:22:22</OPERATOR_TIME>" +
                "<ItemHisID>" +
                "<HIS_ID>80000055973</HIS_ID>" +
                "<HIS_ID>80000055975</HIS_ID>" +
                "</ItemHisID>" +
                "<REQUISITION_STATE>samplinged</REQUISITION_STATE>" +
                "<patid>102601150002</patid>" +
                "<OPERATOR_PERSON>张三</OPERATOR_PERSON>" +
                "<REQUISITION_ID>2103418066</REQUISITION_ID>" +
                "</Request>";
        Map<String, Object> metaMap = new HashMap<>();

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // Request 下有多种标签 → 各自作为对象属性
        assertEquals("2026-01-29 12:22:22", result.get("OPERATOR_TIME"));
        assertEquals("samplinged", result.get("REQUISITION_STATE"));
        assertEquals("102601150002", result.get("patid"));
        assertEquals("张三", result.get("OPERATOR_PERSON"));
        assertEquals("2103418066", result.get("REQUISITION_ID"));

        // ItemHisID 下只有 HIS_ID 一种标签 → 简化为数组
        assertTrue(result.get("ItemHisID") instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> itemHisIds = (List<Object>) result.get("ItemHisID");
        assertEquals(2, itemHisIds.size());
        assertEquals("80000055973", itemHisIds.get(0));
        assertEquals("80000055975", itemHisIds.get(1));
    }
}
