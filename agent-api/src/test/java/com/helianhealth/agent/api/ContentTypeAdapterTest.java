package com.helianhealth.agent.api;

import com.helianhealth.agent.api.adapter.ContentTypeAdapter;
import com.helianhealth.agent.common.enums.WorkflowContentType;
import com.helianhealth.agent.common.utils.XmlUtils;
import com.helianhealth.agent.workflow.model.domain.InterfaceWorkflowDO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContentTypeAdapterTest {

    private static ContentTypeAdapter contentTypeAdapter;

    @BeforeAll
    public static void setUp() {
        contentTypeAdapter = new ContentTypeAdapter();
    }

    // 测试通过标签名定位业务数据&JSON格式参数
    @Test
    void testLocationXmlBizDataWithRequestElementName() {
        // 1. 准备SOAP XML数据（JDK 8兼容的字符串拼接）
        String soapXml = "<soapenv:Envelope\n" +
                "    xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"\n" +
                "    xmlns:api=\"http://api.physicalv.ws.manager.cdxt.com/\">\n" +
                "    <soapenv:Header/>\n" +
                "    <soapenv:Body>\n" +
                "        <api:saveItem>\n" +
                "            <arg0>\n" +
                "                <request>\n" +
                "                    <![CDATA[{\n" +
                "  \"OrderDeptId\": \"ef70856500768081165f\",\n" +
                "  \"OrderDoctId\": \"f4d8bfd1026480812da6\",\n" +
                "  \"PatientInfo\": {\n" +
                "    \"Birthday\": \"2003-09-08\",\n" +
                "    \"IDCardNo\": \"340602200309086130\",\n" +
                "    \"IsGroup\": \"2\",\n" +
                "    \"IsUnitInvoice\": \"2\",\n" +
                "    \"Name\": \"测试10271\",\n" +
                "    \"Nation\": \"汉\",\n" +
                "    \"Phone\": \"15365472603\",\n" +
                "    \"Sex\": \"男\",\n" +
                "    \"payerPartyCode\": \"\",\n" +
                "    \"payerPartyName\": \"\"\n" +
                "  }\n" +
                "}]]>\n" +
                "                </request>\n" +
                "            </arg0>\n" +
                "        </api:saveItem>\n" +
                "    </soapenv:Body>\n" +
                "</soapenv:Envelope>";

        // 2. 准备metaMap参数
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put("elementName", "request"); // 第一种定位方式：通过标签名
        metaMap.put("dataType", "json"); // 业务数据为JSON格式

        // 3. 执行解析方法
        Map<String, Object> resultMap = XmlUtils.parseRequestXml(soapXml, metaMap);

        // 4. 验证解析结果
        assertNotNull(resultMap, "解析结果不能为null");
        assertFalse(resultMap.isEmpty(), "解析结果不能为空");

        // 验证顶层字段
        assertEquals("ef70856500768081165f", resultMap.get("OrderDeptId"), "OrderDeptId解析错误");
        assertEquals("f4d8bfd1026480812da6", resultMap.get("OrderDoctId"), "OrderDoctId解析错误");

        // 验证嵌套对象PatientInfo
        @SuppressWarnings("unchecked")
        Map<String, Object> patientInfo = (Map<String, Object>) resultMap.get("PatientInfo");
        assertNotNull(patientInfo, "PatientInfo未解析");
        assertEquals("测试10271", patientInfo.get("Name"), "姓名解析错误");
        assertEquals("340602200309086130", patientInfo.get("IDCardNo"), "身份证号解析错误");
        assertEquals("男", patientInfo.get("Sex"), "性别解析错误");
        assertEquals("2003-09-08", patientInfo.get("Birthday"), "生日解析错误");
    }

    /**
     * 测试通过命名空间定位目标业务数据
     */
    @Test
    void testLocationXmlBizDataWithNamespace() {
        // 1. 准备带命名空间的SOAP XML（业务数据在api命名空间下的request标签中）
        String soapXml = "<soapenv:Envelope\n" +
                "    xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"\n" +
                "    xmlns:api=\"http://api.physicalv.ws.manager.cdxt.com/\">\n" +
                "    <soapenv:Header/>\n" +
                "    <soapenv:Body>\n" +
                "        <api:saveItem>\n" +
                "            <param>\n" +  // 外层元素
                "                <request>\n" +  // 业务数据容器
                "                    <![CDATA[{\n" +
                "  \"OrderDeptId\": \"ef70856500768081165f\",\n" +
                "  \"OrderDoctId\": \"f4d8bfd1026480812da6\",\n" +
                "  \"PatientInfo\": {\n" +
                "    \"Birthday\": \"2003-09-08\",\n" +
                "    \"IDCardNo\": \"340602200309086130\",\n" +
                "    \"Name\": \"测试10271\",\n" +
                "    \"Sex\": \"男\"\n" +
                "  }\n" +
                "}]]>\n" +
                "                </request>\n" +
                "            </param>\n" +
                "        </api:saveItem>\n" +
                "    </soapenv:Body>\n" +
                "</soapenv:Envelope>";

        // 2. 准备metaMap，配置命名空间相关参数
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put("namespace", "http://api.physicalv.ws.manager.cdxt.com/");
        metaMap.put("namespaceElementName", "saveItem");
        // 通过.递归获取业务数据
        metaMap.put("namespaceDataElementName", "param.request");
        metaMap.put("dataType", "json"); // 业务数据为JSON格式

        // 3. 执行解析方法
        Map<String, Object> resultMap = XmlUtils.parseRequestXml(soapXml, metaMap);

        // 4. 验证解析结果
        assertNotNull(resultMap, "解析结果不能为null");
        assertFalse(resultMap.isEmpty(), "解析结果不能为空");

        // 验证顶层字段
        assertEquals("ef70856500768081165f", resultMap.get("OrderDeptId"), "OrderDeptId解析错误");
        assertEquals("f4d8bfd1026480812da6", resultMap.get("OrderDoctId"), "OrderDoctId解析错误");

        // 验证嵌套对象
        @SuppressWarnings("unchecked")
        Map<String, Object> patientInfo = (Map<String, Object>) resultMap.get("PatientInfo");
        assertNotNull(patientInfo, "PatientInfo未解析");
        assertEquals("测试10271", patientInfo.get("Name"), "姓名解析错误");
        assertEquals("男", patientInfo.get("Sex"), "性别解析错误");
    }

    /**
     * 测试通过命名空间定位目标xml业务数据,不需要.递归
     */
    @Test
    void testLocationXmlBizDataWithNamespace2() {
        // 1. 准备带命名空间的SOAP XML（业务数据为XML格式，在request标签中）
        String soapXml = "<soapenv:Envelope\n" +
                "    xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"\n" +
                "    xmlns:api=\"http://api.physicalv.ws.manager.cdxt.com/\">\n" +
                "    <soapenv:Header/>\n" +
                "    <soapenv:Body>\n" +
                "        <api:saveItem>\n" +
                "            <request\n>" +  // 业务数据容器
                "                  <OrderDeptId>ef70856500768081165f</OrderDeptId>\n" +
                "                  <OrderDoctId>f4d8bfd1026480812da6</OrderDoctId>\n" +
                "                  <PatientInfo>\n" +
                "                       <Birthday>2003-09-08</Birthday>\n" +
                "                       <IDCardNo>340602200309086130</IDCardNo>\n" +
                "                       <Name>测试10271</Name>\n" +
                "                       <Sex>男</Sex>\n" +
                "                   </PatientInfo>\n" +
                "             </request\n>" +
                "        </api:saveItem>\n" +
                "    </soapenv:Body>\n" +
                "</soapenv:Envelope>";

        // 2. 准备metaMap，配置命名空间相关参数
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put("namespace", "http://api.physicalv.ws.manager.cdxt.com/");
        metaMap.put("namespaceElementName", "saveItem");
        // 获取业务数据
        metaMap.put("namespaceDataElementName", "request");
        metaMap.put("isCDATA", "false");

        // 3. 执行解析方法
        Map<String, Object> resultMap = XmlUtils.parseRequestXml(soapXml, metaMap);

        // 4. 验证解析结果
        assertNotNull(resultMap, "解析结果不能为null");
        assertFalse(resultMap.isEmpty(), "解析结果不能为空");

        // 验证顶层字段
        assertEquals("ef70856500768081165f", resultMap.get("OrderDeptId"), "OrderDeptId解析错误");
        assertEquals("f4d8bfd1026480812da6", resultMap.get("OrderDoctId"), "OrderDoctId解析错误");

        // 验证嵌套对象
        @SuppressWarnings("unchecked")
        Map<String, Object> patientInfo = (Map<String, Object>) resultMap.get("PatientInfo");
        assertNotNull(patientInfo, "PatientInfo未解析");
        assertEquals("测试10271", patientInfo.get("Name"), "姓名解析错误");
        assertEquals("男", patientInfo.get("Sex"), "性别解析错误");
    }



    /**
     * 测试通用XML格式数据解析:标签体
     */
    @Test
    public void testParseXmlFormatWithObjectAndArray() {

        String xmlString = "<HrRequest>\n" +
                "    <Head>\n" +
                "        <TradeCode>LAB102</TradeCode>\n" +
                "        <HospitalCode>03</HospitalCode>\n" +
                "        <TradeTime/>\n" +
                "        <SystemCode>LIS</SystemCode>\n" +
                "    </Head>\n" +
                "    <Body>\n" +
                "        <PatientId>030001001</PatientId>\n" +
                "        <VisitNo>20250806000001</VisitNo>\n" +
                "        <ApplyNo>20250807G0030165</ApplyNo>\n" +
                "        <LabStatus>F</LabStatus>\n" +
                "        <Result>\n" +
                "            <LabOrderResultList>\n" +
                "                <ItemNo>20</ItemNo>\n" +
                "                <ItemName>平均血小板体积</ItemName>\n" +
                "                <Result>7.0</Result>\n" +
                "                <Units>fl</Units>\n" +
                "            </LabOrderResultList>\n" +
                "            <LabOrderResultList>\n" +
                "                <ItemNo>21</ItemNo>\n" +
                "                <ItemName>血小板压积</ItemName>\n" +
                "                <Result>0.120</Result>\n" +
                "                <Units>%</Units>\n" +
                "            </LabOrderResultList>\n" +
                "        </Result>\n" +
                "    </Body>\n" +
                "</HrRequest>";

        Map<String, Object> result = XmlUtils.parseRequestXml(xmlString,  new HashMap<>());

        // 验证结果
        assertNotNull(result);
        assertTrue(result.containsKey("Head"));
        assertTrue(result.containsKey("Body"));

        // 验证Head部分
        Map<String, Object> head = (Map<String, Object>) result.get("Head");
        assertEquals("LAB102", head.get("TradeCode"));
        assertEquals("03", head.get("HospitalCode"));
        assertEquals("LIS", head.get("SystemCode"));
        // 空值验证
        assertNull(head.get("TradeTime"));

        // 验证Body部分
        Map<String, Object> body = (Map<String, Object>) result.get("Body");
        assertEquals("030001001", body.get("PatientId"));
        assertEquals("20250806000001", body.get("VisitNo"));

        // 验证Result数组
        List<Map<String, Object>> resultList = (List<Map<String, Object>>) body.get("Result");
        assertEquals(2, resultList.size());

        // 验证第一个结果项
        Map<String, Object> firstItem = resultList.get(0);
        assertEquals("20", firstItem.get("ItemNo"));
        assertEquals("平均血小板体积", firstItem.get("ItemName"));
        assertEquals("7.0", firstItem.get("Result"));

        // 验证第二个结果项
        Map<String, Object> secondItem = resultList.get(1);
        assertEquals("21", secondItem.get("ItemNo"));
        assertEquals("血小板压积", secondItem.get("ItemName"));
        assertEquals("0.120", secondItem.get("Result"));
    }

    @Test
    public void testParseRequestXmlFormatWithPureArrayData() {
        // 准备数据
        InterfaceWorkflowDO workflow = new InterfaceWorkflowDO();
        workflow.setContentType(WorkflowContentType.XML);

        String xmlString = "<HrRequest>\n" +
                "    <Head>\n" +
                "        <TradeCode>LAB203</TradeCode>\n" +
                "        <TradeTime>2025-09-19 12:20:09</TradeTime>\n" +
                "        <TradeNo>3F20C905A3A79FF1E0631F1010ACD55B</TradeNo>\n" +
                "        <BranchCode>01</BranchCode>\n" +
                "        <HospitalCode></HospitalCode>\n" +
                "        <SystemCode>HIS</SystemCode>\n" +
                "        <HipKeyNo>xBzrURjFUl4Q1E9nTaCu5C==</HipKeyNo>\n" +
                "    </Head>\n" +
                "    <Body>\n" +
                "        <patientID>030001406</patientID>\n" +
                "        <visitNo>20250919000102</visitNo>\n" +
                "        <orderId>\n" +
                "            <orderIdList>2025091900000213</orderIdList>\n" +
                "            <orderIdList>2025091900000214</orderIdList>\n" +
                "        </orderId>\n" +
                "        <reportId>20250918G0029999</reportId>\n" +
                "        <operDocId>2011031_章利群</operDocId>\n" +
                "        <operDocName>2011031_章利群</operDocName>\n" +
                "        <operTime>2025-09-19 12:20:09</operTime>\n" +
                "    </Body>\n" +
                "</HrRequest>";

        Map<String, Object> result = contentTypeAdapter.adaptRequest(workflow, xmlString);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.containsKey("Head"));
        assertTrue(result.containsKey("Body"));

        // 验证Head部分
        Map<String, Object> head = (Map<String, Object>) result.get("Head");
        assertEquals("LAB203", head.get("TradeCode"));
        assertEquals("2025-09-19 12:20:09", head.get("TradeTime"));
        assertEquals("3F20C905A3A79FF1E0631F1010ACD55B", head.get("TradeNo"));
        assertEquals("01", head.get("BranchCode"));
        assertNull(head.get("HospitalCode")); // 空字符串应该解析为null
        assertEquals("HIS", head.get("SystemCode"));
        assertEquals("xBzrURjFUl4Q1E9nTaCu5C==", head.get("HipKeyNo"));

        // 验证Body部分
        Map<String, Object> body = (Map<String, Object>) result.get("Body");
        assertEquals("030001406", body.get("patientID"));
        assertEquals("20250919000102", body.get("visitNo"));
        assertEquals("20250918G0029999", body.get("reportId"));
        assertEquals("2011031_章利群", body.get("operDocId"));
        assertEquals("2011031_章利群", body.get("operDocName"));
        assertEquals("2025-09-19 12:20:09", body.get("operTime"));

        // 验证orderId数组
        List<String> orderIdList = (List<String>) body.get("orderId");
        assertEquals(2, orderIdList.size());

        // 验证第一个orderId
        assertEquals("2025091900000213", orderIdList.get(0));

        // 验证第二个orderId
        assertEquals("2025091900000214", orderIdList.get(1));
    }

    @Test
    public void testParseRequestXmlFormatWithPureSingleArrayData() {
        // 准备数据
        InterfaceWorkflowDO workflow = new InterfaceWorkflowDO();
        workflow.setContentType(WorkflowContentType.XML);

        String xmlString = "<HrRequest>\n" +
                "    <Head>\n" +
                "        <TradeCode>LAB203</TradeCode>\n" +
                "        <TradeTime>2025-09-19 12:20:09</TradeTime>\n" +
                "        <TradeNo>3F20C905A3A79FF1E0631F1010ACD55B</TradeNo>\n" +
                "        <BranchCode>01</BranchCode>\n" +
                "        <HospitalCode></HospitalCode>\n" +
                "        <SystemCode>HIS</SystemCode>\n" +
                "        <HipKeyNo>xBzrURjFUl4Q1E9nTaCu5C==</HipKeyNo>\n" +
                "    </Head>\n" +
                "    <Body>\n" +
                "        <patientID>030001406</patientID>\n" +
                "        <visitNo>20250919000102</visitNo>\n" +
                "        <orderId>\n" +
                "            <orderIdList>2025091900000213</orderIdList>\n" +
                "        </orderId>\n" +
                "        <reportId>20250918G0029999</reportId>\n" +
                "        <operDocId>2011031_章利群</operDocId>\n" +
                "        <operDocName>2011031_章利群</operDocName>\n" +
                "        <operTime>2025-09-19 12:20:09</operTime>\n" +
                "    </Body>\n" +
                "</HrRequest>";

        Map<String, Object> result = contentTypeAdapter.adaptRequest(workflow, xmlString);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.containsKey("Head"));
        assertTrue(result.containsKey("Body"));

        // 验证Head部分
        Map<String, Object> head = (Map<String, Object>) result.get("Head");
        assertEquals("LAB203", head.get("TradeCode"));
        assertEquals("2025-09-19 12:20:09", head.get("TradeTime"));
        assertEquals("3F20C905A3A79FF1E0631F1010ACD55B", head.get("TradeNo"));
        assertEquals("01", head.get("BranchCode"));
        assertNull(head.get("HospitalCode")); // 空字符串应该解析为null
        assertEquals("HIS", head.get("SystemCode"));
        assertEquals("xBzrURjFUl4Q1E9nTaCu5C==", head.get("HipKeyNo"));

        // 验证Body部分
        Map<String, Object> body = (Map<String, Object>) result.get("Body");
        assertEquals("030001406", body.get("patientID"));
        assertEquals("20250919000102", body.get("visitNo"));
        assertEquals("20250918G0029999", body.get("reportId"));
        assertEquals("2011031_章利群", body.get("operDocId"));
        assertEquals("2011031_章利群", body.get("operDocName"));
        assertEquals("2025-09-19 12:20:09", body.get("operTime"));

        // 验证orderId数组
        List<String> orderIdList = (List<String>) body.get("orderId");
        assertEquals(1, orderIdList.size());

        // 验证第一个orderId
        assertEquals("2025091900000213", orderIdList.get(0));
    }
}
