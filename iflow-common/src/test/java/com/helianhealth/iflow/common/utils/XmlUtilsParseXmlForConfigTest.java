package com.helianhealth.iflow.common.utils;

import com.helianhealth.iflow.common.base.MetaInfoKeyConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XmlUtils 测试用例
 * 覆盖场景：XML解析、Map转XML、业务数据处理
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
        metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "Result");

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
        metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "Result");

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
        metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "NotExist");

        assertThrows(RuntimeException.class, () -> XmlUtils.parseResponseXml(xml, metaMap));
    }

    @Test
    public void testElementName_MultipleNodes() {
        // elementName 对应多个节点
        String xml = "<Root><Item>1</Item><Item>2</Item></Root>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "Item");

        assertThrows(RuntimeException.class, () -> XmlUtils.parseResponseXml(xml, metaMap));
    }

    // ==================== 3. innerElementName 内层二级定位 ====================

    @Test
    public void testInnerElementName_Locate() {
        // 使用 innerElementName 进一步定位
        String xml = "<Body><Result><![CDATA[<root><wrapper><data><id>002</id></data></wrapper></root>]]></Result></Body>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "Result");
        metaMap.put(MetaInfoKeyConstants.INNER_ELEMENT_NAME, "data");

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // data 下只有 id 一种标签 → 数组
        assertInstanceOf(List.class, result.get("id"));
        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) result.get("id");
        assertEquals("002", ids.get(0));
    }

    @Test
    public void testInnerElementName_NotFound_ThrowException() {
        // innerElementName 不存在，抛出异常
        String xml = "<Body><Result><![CDATA[<data><id>003</id></data>]]></Result></Body>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "Result");
        metaMap.put(MetaInfoKeyConstants.INNER_ELEMENT_NAME, "notExist");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> XmlUtils.parseResponseXml(xml, metaMap));
        assertTrue(exception.getMessage().contains("内层标签[notExist]未找到"));
    }

    @Test
    public void testInnerElementName_CdataWithXmlDeclaration() {
        // 场景：CDATA 内容包含 XML 声明（<?xml version="1.0"?>），使用 innerElementName 定位业务数据
        // 这是 WebService 调用常见的响应格式
        String xml = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                "<soap:Body>" +
                "<GetDataResponse>" +
                "<Result><![CDATA[<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<response>" +
                "<header><code>0</code><message>success</message></header>" +
                "<body><patient><id>P001</id><name>张三</name></patient></body>" +
                "</response>]]></Result>" +
                "</GetDataResponse>" +
                "</soap:Body>" +
                "</soap:Envelope>";

        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "Result");         // 定位外层 CDATA 容器
        metaMap.put(MetaInfoKeyConstants.INNER_ELEMENT_NAME, "body");     // 定位内层业务数据节点

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // 验证：从 body 节点开始解析，跳过 header
        assertInstanceOf(List.class, result.get("patient"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> patients = (List<Map<String, Object>>) result.get("patient");
        assertEquals("P001", patients.get(0).get("id"));
        assertEquals("张三", patients.get(0).get("name"));
    }

    // ==================== 4. isCDATA 配置场景 ====================

    @Test
    public void testIsCDATA_False() {
        // isCDATA=false，直接取子元素
        String xml = "<Body><Result><patient><code>P002</code></patient></Result></Body>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "Result");
        metaMap.put(MetaInfoKeyConstants.IS_CDATA, false);

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
        metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "Result");

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
        metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "Result");
        metaMap.put(MetaInfoKeyConstants.DATA_TYPE, "json");

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        assertEquals("J001", result.get("code"));
        assertEquals("测试", result.get("name"));
    }

    @Test
    public void testDataType_JsonArray() {
        // CDATA 内容是 JSON 数组
        String xml = "<Body><Result><![CDATA[{\"items\":[{\"id\":1},{\"id\":2}]}]]></Result></Body>";
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "Result");
        metaMap.put(MetaInfoKeyConstants.DATA_TYPE, "JSON");

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
        metaMap.put(MetaInfoKeyConstants.USE_ESCAPE, true);

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
        metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "Result");

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
        metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "Result");
        metaMap.put(MetaInfoKeyConstants.INNER_ELEMENT_NAME, "data");

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
        metaMap.put(MetaInfoKeyConstants.OBJECT_TAG, Collections.singletonList("items"));

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

    @Test
    public void testDirectXmlParsing_NoMetadata() {
        // 直接解析 XML，不带任何元信息配置
        // 检验报告系统推送状态到 LIS 系统的 XML 格式
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<body>" +
                "<head>" +
                "<userid>zhrmyy</userid>" +
                "<password>123</password>" +
                "<trans_no>assay_to_hltj</trans_no>" +
                "</head>" +
                "<state_to_hltj>" +
                "<APPLY_NO>2026030650159</APPLY_NO>" +
                "<PATIENTCODE>259038</PATIENTCODE>" +
                "<OPERATENAME>刘晶</OPERATENAME>" +
                "<OPERATETIME>2026-03-06 11:24:50</OPERATETIME>" +
                "<STATE>90</STATE>" +
                "<CHECK_TYPE>assay</CHECK_TYPE>" +
                "</state_to_hltj>" +
                "</body>";
        Map<String, Object> metaMap = new HashMap<>();

        Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

        // body 下有 head 和 state_to_hltj 两种标签 → 各自作为对象属性
        assertInstanceOf(Map.class, result.get("head"));
        assertInstanceOf(Map.class, result.get("state_to_hltj"));

        // 验证 head 内容：有三种子标签，各字段直接是字符串值
        @SuppressWarnings("unchecked")
        Map<String, Object> head = (Map<String, Object>) result.get("head");
        assertEquals("zhrmyy", head.get("userid"));
        assertEquals("123", head.get("password"));
        assertEquals("assay_to_hltj", head.get("trans_no"));

        // 验证 state_to_hltj 内容：有六种子标签，各字段直接是字符串值
        @SuppressWarnings("unchecked")
        Map<String, Object> stateToHltj = (Map<String, Object>) result.get("state_to_hltj");
        assertEquals("2026030650159", stateToHltj.get("APPLY_NO"));
        assertEquals("259038", stateToHltj.get("PATIENTCODE"));
        assertEquals("刘晶", stateToHltj.get("OPERATENAME"));
        assertEquals("2026-03-06 11:24:50", stateToHltj.get("OPERATETIME"));
        assertEquals("90", stateToHltj.get("STATE"));
        assertEquals("assay", stateToHltj.get("CHECK_TYPE"));
    }

    // ==================== 12. Map转XML场景（buildResponseXml） ====================

    @Nested
    @DisplayName("Map转XML场景")
    class BuildResponseXmlTests {

        @Test
        @DisplayName("简单Map转XML")
        void buildSimpleXml() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("code", "200");
            data.put("message", "成功");

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("Response", data);

            String xml = XmlUtils.buildResponseXml(root, "{}");

            assertNotNull(xml);
            assertTrue(xml.contains("<Response>"));
            assertTrue(xml.contains("<code>200</code>"));
            assertTrue(xml.contains("<message>成功</message>"));
        }

        @Test
        @DisplayName("嵌套Map转XML")
        void buildNestedXml() {
            Map<String, Object> patient = new LinkedHashMap<>();
            patient.put("code", "P001");
            patient.put("name", "张三");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("patient", patient);

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("Response", data);

            String xml = XmlUtils.buildResponseXml(root, "{}");

            assertTrue(xml.contains("<patient>"));
            assertTrue(xml.contains("<code>P001</code>"));
            assertTrue(xml.contains("<name>张三</name>"));
        }

        @Test
        @DisplayName("包含数组的Map转XML")
        void buildXmlWithList() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items", Arrays.asList("item1", "item2", "item3"));

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("Response", data);

            String xml = XmlUtils.buildResponseXml(root, "{}");

            assertTrue(xml.contains("<items>item1</items>"));
            assertTrue(xml.contains("<items>item2</items>"));
            assertTrue(xml.contains("<items>item3</items>"));
        }

        @Test
        @DisplayName("包含Map数组的转换")
        void buildXmlWithMapList() {
            Map<String, Object> item1 = new LinkedHashMap<>();
            item1.put("id", "1");
            item1.put("name", "A");

            Map<String, Object> item2 = new LinkedHashMap<>();
            item2.put("id", "2");
            item2.put("name", "B");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("item", Arrays.asList(item1, item2));

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("Response", data);

            String xml = XmlUtils.buildResponseXml(root, "{}");

            // 验证两个item元素
            assertTrue(xml.contains("<id>1</id>"));
            assertTrue(xml.contains("<name>A</name>"));
            assertTrue(xml.contains("<id>2</id>"));
            assertTrue(xml.contains("<name>B</name>"));
        }

        @Test
        @DisplayName("带命名空间的XML生成")
        void buildXmlWithNamespace() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("code", "200");

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("Response", data);

            String metaInfo = "{\"responseNamespace\":\"http://example.com/ws\"}";
            String xml = XmlUtils.buildResponseXml(root, metaInfo);

            assertTrue(xml.contains("xmlns=\"http://example.com/ws\""));
        }

        @Test
        @DisplayName("指定resultElementName")
        void buildXmlWithResultElementName() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("code", "200");

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("CustomResponse", data);

            String metaInfo = "{\"resultElementName\":\"CustomResponse\"}";
            String xml = XmlUtils.buildResponseXml(root, metaInfo);

            assertTrue(xml.contains("<CustomResponse>"));
        }

        @Test
        @DisplayName("null值生成空标签")
        void buildXmlWithNullValue() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("code", "200");
            data.put("remark", null);

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("Response", data);

            String xml = XmlUtils.buildResponseXml(root, "{}");

            assertTrue(xml.contains("<remark></remark>") || xml.contains("<remark/>"));
        }

        @Test
        @DisplayName("带属性的Map转XML")
        void buildXmlWithAttributes() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("#version", "1.0");  // # 前缀表示属性
            data.put("code", "200");

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("Response", data);

            String xml = XmlUtils.buildResponseXml(root, "{}");

            assertTrue(xml.contains("version=\"1.0\""));
            assertTrue(xml.contains("<code>200</code>"));
        }

        @Test
        @DisplayName("空Map返回空字符串")
        void buildXml_EmptyMap() {
            assertEquals("", XmlUtils.buildResponseXml(new HashMap<>(), "{}"));
        }

        @Test
        @DisplayName("空Map且配置SOAP模板仍走模板适配")
        void buildXml_EmptyMap_WithSoapTemplate() {
            String metaInfo = "{\"soapRspTemplate\":\"<soap>${outputParam}</soap>\"}";
            assertEquals("<soap></soap>", XmlUtils.buildResponseXml(new HashMap<>(), metaInfo));
        }

        @Test
        @DisplayName("null Map返回空字符串")
        void buildXml_NullMap() {
            assertEquals("", XmlUtils.buildResponseXml(null, "{}"));
        }

        @Test
        @DisplayName("null Map且配置SOAP模板仍走模板适配")
        void buildXml_NullMap_WithSoapTemplate() {
            String metaInfo = "{\"soapRspTemplate\":\"<soap>${outputParam}</soap>\"}";
            assertEquals("<soap></soap>", XmlUtils.buildResponseXml(null, metaInfo));
        }
    }

    // ==================== 13. 业务响应场景 ====================

    @Nested
    @DisplayName("业务响应场景")
    class BusinessResponseTests {

        @Test
        @DisplayName("档案创建响应：HIS返回患者ID")
        void archivesCreationResponse() {
            String xml = "<CreateArchivesResponse>" +
                    "<returnCode>1</returnCode>" +
                    "<returnMsg>成功</returnMsg>" +
                    "<hisPatientId>HIS001</hisPatientId>" +
                    "</CreateArchivesResponse>";
            Map<String, Object> metaMap = new HashMap<>();

            Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

            assertEquals("1", result.get("returnCode"));
            assertEquals("成功", result.get("returnMsg"));
            assertEquals("HIS001", result.get("hisPatientId"));
        }

        @Test
        @DisplayName("账单创建响应：HIS返回账单ID")
        void orderCreationResponse() {
            String xml = "<ChargeOrderResponse>" +
                    "<resultCode>SUCCESS</resultCode>" +
                    "<hisOrderId>ORD12345</hisOrderId>" +
                    "<chargeTime>2026-01-01 10:00:00</chargeTime>" +
                    "</ChargeOrderResponse>";
            Map<String, Object> metaMap = new HashMap<>();

            Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

            assertEquals("SUCCESS", result.get("resultCode"));
            assertEquals("ORD12345", result.get("hisOrderId"));
        }

        @Test
        @DisplayName("检验申请单响应：包含多个项目")
        void labRequestResponse() {
            String xml = "<LabRequestResponse>" +
                    "<returnCode>1</returnCode>" +
                    "<items>" +
                    "<item><itemCode>BLD001</itemCode><status>SUCCESS</status></item>" +
                    "<item><itemCode>BLD002</itemCode><status>SUCCESS</status></item>" +
                    "</items>" +
                    "</LabRequestResponse>";
            Map<String, Object> metaMap = new HashMap<>();

            Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

            assertEquals("1", result.get("returnCode"));
            assertInstanceOf(Map.class, result.get("items"));
            @SuppressWarnings("unchecked")
            Map<String, Object> items = (Map<String, Object>) result.get("items");
            assertInstanceOf(List.class, items.get("item"));
        }

        @Test
        @DisplayName("WebService SOAP响应解析")
        void soapResponseParsing() {
            String xml = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                    "<soap:Body>" +
                    "<GetPatientResponse>" +
                    "<Result><![CDATA[{\"code\":\"200\",\"patientId\":\"P001\"}]]></Result>" +
                    "</GetPatientResponse>" +
                    "</soap:Body>" +
                    "</soap:Envelope>";
            Map<String, Object> metaMap = new HashMap<>();
            metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "Result");
            metaMap.put(MetaInfoKeyConstants.DATA_TYPE, "json");

            Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

            assertEquals("200", result.get("code"));
            assertEquals("P001", result.get("patientId"));
        }

        @Test
        @DisplayName("SOAP响应解析：成功时有Body和Head标签")
        void soapResponseParsing_SuccessWithBodyAndHead() {
            // 成功场景：响应数据包含 Body 和 Head
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
                    "<SOAP-ENV:Envelope xmlns:SOAP-ENV='http://schemas.xmlsoap.org/soap/envelope/' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns:s='http://www.w3.org/2001/XMLSchema'>" +
                    "<SOAP-ENV:Body>" +
                    "<MessageInResponse xmlns=\"http://herenit.com\">" +
                    "<MessageInResult><![CDATA[<HrResponse><Body><PatientID>P0005845</PatientID><VisitNo>20260326003015</VisitNo></Body><Head><HipSessionId>215427605</HipSessionId><SystemCode>TJ</SystemCode><TradeCode>PAT202</TradeCode><TradeMessage>成功</TradeMessage><TradeStatus>AA</TradeStatus><TradeTime>2026-03-26 10:59:53</TradeTime></Head></HrResponse>]]></MessageInResult>" +
                    "</MessageInResponse>" +
                    "</SOAP-ENV:Body>" +
                    "</SOAP-ENV:Envelope>";

            Map<String, Object> metaMap = new HashMap<>();
            metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "MessageInResult");

            Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

            // 验证 Head 字段
            assertInstanceOf(Map.class, result.get("Head"));
            @SuppressWarnings("unchecked")
            Map<String, Object> head = (Map<String, Object>) result.get("Head");
            assertEquals("215427605", head.get("HipSessionId"));
            assertEquals("TJ", head.get("SystemCode"));
            assertEquals("PAT202", head.get("TradeCode"));
            assertEquals("成功", head.get("TradeMessage"));
            assertEquals("AA", head.get("TradeStatus"));
            assertEquals("2026-03-26 10:59:53", head.get("TradeTime"));

            // 验证 Body 字段
            assertInstanceOf(Map.class, result.get("Body"));
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) result.get("Body");
            assertEquals("P0005845", body.get("PatientID"));
            assertEquals("20260326003015", body.get("VisitNo"));
        }

        @Test
        @DisplayName("SOAP响应解析：失败时只有Head标签无Body")
        void soapResponseParsing_FailureOnlyHead() {
            // 失败场景：响应数据只有 Head，没有 Body
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
                    "<SOAP-ENV:Envelope xmlns:SOAP-ENV='http://schemas.xmlsoap.org/soap/envelope/' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns:s='http://www.w3.org/2001/XMLSchema'>" +
                    "<SOAP-ENV:Body>" +
                    "<MessageInResponse xmlns=\"http://herenit.com\">" +
                    "<MessageInResult><![CDATA[<HrResponse><Head><HipSessionId>215468541</HipSessionId><SystemCode>TJ</SystemCode><TradeCode>PAT202</TradeCode><TradeMessage>与证件匹配的患者，姓名都不是测试!</TradeMessage><TradeStatus>AE</TradeStatus><TradeTime>2026-03-26 11:08:58</TradeTime></Head></HrResponse>]]></MessageInResult>" +
                    "</MessageInResponse>" +
                    "</SOAP-ENV:Body>" +
                    "</SOAP-ENV:Envelope>";

            Map<String, Object> metaMap = new HashMap<>();
            metaMap.put(MetaInfoKeyConstants.ELEMENT_NAME, "MessageInResult");

            metaMap.put(MetaInfoKeyConstants.OBJECT_TAG, Collections.singletonList("HrResponse"));

            Map<String, Object> result = XmlUtils.parseResponseXml(xml, metaMap);

            // Head 始终是 Map 结构，与成功场景保持一致
            assertTrue(result.containsKey("Head"));
            assertInstanceOf(Map.class, result.get("Head"));
            @SuppressWarnings("unchecked")
            Map<String, Object> head = (Map<String, Object>) result.get("Head");

            assertEquals("215468541", head.get("HipSessionId"));
            assertEquals("TJ", head.get("SystemCode"));
            assertEquals("PAT202", head.get("TradeCode"));
            assertEquals("与证件匹配的患者，姓名都不是测试!", head.get("TradeMessage"));
            assertEquals("AE", head.get("TradeStatus"));
            assertEquals("2026-03-26 11:08:58", head.get("TradeTime"));
        }
    }
}
