package com.helianhealth.agent.utils;

import com.helianhealth.agent.common.utils.XmlUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XmlUtilsTest {

    @Test
    public void testXmlToMap() {
        // 准备测试XML数据
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<person>" +
                "    <name>张三</name>" +
                "    <age>30</age>\n" +
                "    <address type=\"home\">" +
                "        <street>长安街1号</street>" +
                "        <city>北京</city>" +
                "    </address>" +
                "    <phone>13800138000</phone>" +
                "    <phone>13900139000</phone>" +
                "</person>";

        // 执行转换
        Map<String, Object> result = XmlUtils.xmlToMap(xml);

        assertNotNull(result);
        assertEquals("张三", result.get("name"));
        assertEquals("30", result.get("age"));

        // 验证嵌套元素
        Map<String, Object> address = (Map<String, Object>) result.get("address");
        assertEquals("home", address.get("type"));
        assertEquals("长安街1号", address.get("street"));

        // 验证重复元素处理
        Object phones = result.get("phone");
        assertTrue(phones instanceof List);
        assertEquals(2, ((List<?>) phones).size());
    }

    @Test
    public void testSimpleElementToMap() {
        String xml = "<root><name>test</name></root>";
        Map<String, Object> result = XmlUtils.xmlToMap(xml);
        assertNotNull(result);
        assertEquals("test", result.get("name"));
    }

    @Test
    public void testElementWithAttributesToMap() {
        String xml = "<root id=\"123\" type=\"test\"><name>test</name></root>";
        Map<String, Object> result = XmlUtils.xmlToMap(xml);

        assertNotNull(result);
        assertEquals("123", result.get("id"));
        assertEquals("test", result.get("type"));
        assertEquals("test", result.get("name"));
    }

    @Test
    public void testParseResponseXml() {
        // 准备测试XML数据
//        StringBuilder soapMessage = new StringBuilder();
//        soapMessage.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
//        soapMessage.append("<SOAP-ENV:Envelope xmlns:SOAP-ENV='http://schemas.xmlsoap.org/soap/envelope/' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns:s='http://www.w3.org/2001/XMLSchema'>\n");
//        soapMessage.append("    <SOAP-ENV:Body>\n");
//        soapMessage.append("        <MessageInResponse xmlns=\"http://herenit.com\">\n");
//        soapMessage.append("            <MessageInResult>\n");
//        soapMessage.append("                <![CDATA[<HrResponse><Head><HipSessionId>7435514</HipSessionId><SystemCode>TJ</SystemCode><TradeCode>PAT202</TradeCode><TradeMessage>{\"message\":\"建立患者档案参数校验失败\",\"errorList\":[{\"beanClass\":\"com.heren.his.bill.core.entity.patient.PatMasterIndex\",\"field\":\"regDate\",\"message\":\"申请日期不能为空\"}]}</TradeMessage><TradeStatus>AE</TradeStatus><TradeTime>2025-09-05 10:03:22</TradeTime></Head></HrResponse>]]>\n");
//        soapMessage.append("            </MessageInResult>\n");
//        soapMessage.append("        </MessageInResponse>\n");
//        soapMessage.append("    </SOAP-ENV:Body>\n");
//        soapMessage.append("</SOAP-ENV:Envelope>");
//
//        // 执行解析
//        Map<String, Object> result = XmlUtils.parseResponseXml(soapMessage.toString());
//
//        // 验证结果
//        System.out.println(JsonUtils.toJsonString(result));

//        // 验证CDATA内容是否正确解析为JSON
//        Map<String, Object> hrResponse = (Map<String, Object>) result.get("HrResponse");
//        assertNotNull(hrResponse);
//
//        Map<String, Object> head = (Map<String, Object>) hrResponse.get("Head");
//        assertNotNull(head);
//
//        assertEquals("7435514", head.get("HipSessionId"));
//        assertEquals("TJ", head.get("SystemCode"));
//        assertEquals("PAT202", head.get("TradeCode"));
//        assertEquals("AE", head.get("TradeStatus"));
//        assertEquals("2025-09-05 10:03:22", head.get("TradeTime"));
//
//        // 验证TradeMessage中的JSON内容
//        String tradeMessage = (String) head.get("TradeMessage");
//        assertNotNull(tradeMessage);
//        assertTrue(tradeMessage.contains("建立患者档案参数校验失败"));
//        assertTrue(tradeMessage.contains("申请日期不能为空"));
    }
}
