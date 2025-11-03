package com.helianhealth.agent.schdule;

import com.helianhealth.agent.enums.WorkflowContentType;
import com.helianhealth.agent.model.domain.InterfaceWorkflowDO;
import com.helianhealth.agent.schedule.ContentParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(
        properties = {
                "spring.profiles.active=local"
        }
)
public class ContentParserTest {

    @Autowired
    private ContentParser contentParser;

    /**
     * 测试通用XML格式数据解析:标签体
     */
    @Test
    public void testParseXmlFormatWithObjectAndArray() {
        // 准备数据
        InterfaceWorkflowDO workflow = new InterfaceWorkflowDO();
        workflow.setContentType(WorkflowContentType.XML);

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

        Map<String, Object> result = contentParser.parseRequest(workflow, xmlString);

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

        Map<String, Object> result = contentParser.parseRequest(workflow, xmlString);

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

        Map<String, Object> result = contentParser.parseRequest(workflow, xmlString);

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
