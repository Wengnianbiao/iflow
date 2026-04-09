package com.helianhealth.agent.common.utils;

import com.helianhealth.agent.common.exception.AgentConfigException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SpelUtils 测试类
 * 覆盖业务场景：入参过滤、值转换、嵌套字段提取、医技调度、后置处理、条件路由
 */
public class SpelUtilsTest {

    // ==================== 入参过滤场景（paramFilterExpr） ====================

    @Nested
    @DisplayName("入参过滤场景")
    class ParamFilterTests {

        @Test
        @DisplayName("收费通知：仅处理 CHARGED 类型")
        void filterByChargeType_Charged() {
            Map<String, Object> data = new HashMap<>();
            data.put("chargeType", "CHARGED");
            data.put("orderId", "ORD001");

            Map<String, Object> result = SpelUtils.businessDataFilter(
                    "#data[chargeType] == 'CHARGED' ? #data : #data.clear()", data);

            assertEquals(2, result.size());
            assertEquals("CHARGED", result.get("chargeType"));
        }

        @Test
        @DisplayName("收费通知：过滤掉 REFUNDED 类型")
        void filterByChargeType_Refunded() {
            Map<String, Object> data = new HashMap<>();
            data.put("chargeType", "REFUNDED");
            data.put("orderId", "ORD001");

            Map<String, Object> result = SpelUtils.businessDataFilter(
                    "#data[chargeType] == 'CHARGED' ? #data : #data.clear()", data);

            assertEquals(0, result.size());
        }

        @Test
        @DisplayName("按业务状态过滤：仅处理有效状态")
        void filterByStatus_Active() {
            Map<String, Object> data = new HashMap<>();
            data.put("status", "ACTIVE");
            data.put("patientCode", "P001");

            Map<String, Object> result = SpelUtils.businessDataFilter(
                    "#data[status] == 'ACTIVE' ? #data : #data.clear()", data);

            assertTrue(result.containsKey("patientCode"));
        }

        @Test
        @DisplayName("过滤空数据")
        void filterNullData() {
            Map<String, Object> result = SpelUtils.businessDataFilter("#data.clear()", null);
            assertNull(result);
        }

        @Test
        @DisplayName("过滤空 Map 返回空 Map")
        void filterEmptyMap() {
            Map<String, Object> result = SpelUtils.businessDataFilter(
                    "#data[chargeType] == 'CHARGED' ? #data : #data.clear()", new HashMap<>());
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("无效表达式抛出 AgentConfigException")
        void filterInvalidExpression() {
            Map<String, Object> data = new HashMap<>();
            data.put("key", "value");
            assertThrows(AgentConfigException.class, () ->
                    SpelUtils.businessDataFilter("#data[", data));
        }

        @Test
        @DisplayName("过滤不修改原始数据（深拷贝验证）")
        void filterDoesNotModifyOriginal() {
            Map<String, Object> data = new HashMap<>();
            data.put("chargeType", "REFUNDED");
            data.put("orderId", "ORD001");

            SpelUtils.businessDataFilter(
                    "#data[chargeType] == 'CHARGED' ? #data : #data.clear()", data);

            assertEquals(2, data.size(), "原始数据不应被修改");
        }
    }

    // ==================== 值转换场景（EXPRESSION mappingType） ====================

    @Nested
    @DisplayName("值转换场景")
    class ValueConversionTests {

        @Test
        @DisplayName("性别转换：1 → 男")
        void genderConversion_Male() {
            Map<String, Object> data = new HashMap<>();
            data.put("sex", "1");

            Object result = SpelUtils.parserPreNode("#data[sex] == '1' ? '男' : '女'", data, null, null);

            assertEquals("男", result);
        }

        @Test
        @DisplayName("性别转换：2 → 女")
        void genderConversion_Female() {
            Map<String, Object> data = new HashMap<>();
            data.put("sex", "2");

            Object result = SpelUtils.parserPreNode("#data[sex] == '1' ? '男' : '女'", data, null, null);

            assertEquals("女", result);
        }

        @Test
        @DisplayName("状态码转换：HIS返回1 → Jarvis期望200")
        void statusCodeConversion_Success() {
            Map<String, Object> response = new HashMap<>();
            response.put("returnCode", "1");

            Object result = SpelUtils.parserPostNode(
                    "#response[returnCode] == '1' ? '200' : '-1'", new HashMap<>(), null, response);

            assertEquals("200", result);
        }

        @Test
        @DisplayName("状态码转换：HIS返回0 → Jarvis期望-1")
        void statusCodeConversion_Failure() {
            Map<String, Object> response = new HashMap<>();
            response.put("returnCode", "0");

            Object result = SpelUtils.parserPostNode(
                    "#response[returnCode] == '1' ? '200' : '-1'", new HashMap<>(), null, response);

            assertEquals("-1", result);
        }

        @Test
        @DisplayName("身份证优先：有身份证时使用身份证")
        void idCardPriority_HasIdCard() {
            Map<String, Object> data = new HashMap<>();
            data.put("idCard", "110101199001011234");
            data.put("phone", "13800138000");

            Object result = SpelUtils.parserPreNode(
                    "#data[idCard] != null ? #data[idCard] : #data[phone] + 'hl'", data, null, null);

            assertEquals("110101199001011234", result);
        }

        @Test
        @DisplayName("身份证优先：无身份证时使用手机号+后缀")
        void idCardPriority_NoIdCard() {
            Map<String, Object> data = new HashMap<>();
            data.put("idCard", null);
            data.put("phone", "13800138000");

            Object result = SpelUtils.parserPreNode(
                    "#data[idCard] != null ? #data[idCard] : #data[phone] + 'hl'", data, null, null);

            assertEquals("13800138000hl", result);
        }

        @Test
        @DisplayName("默认值处理：字段为空时使用默认值")
        void defaultValue_WhenNull() {
            Map<String, Object> data = new HashMap<>();
            data.put("remark", null);

            Object result = SpelUtils.parserPreNode("#data[remark] ?: '无备注'", data, null, null);

            assertEquals("无备注", result);
        }

        @Test
        @DisplayName("字符串拼接：姓名 + 年龄")
        void stringConcatenation() {
            Map<String, Object> data = new HashMap<>();
            data.put("name", "张三");
            data.put("age", "28");

            Object result = SpelUtils.parserPreNode(
                    "#data[name] + '(' + #data[age] + '岁)'", data, null, null);

            assertEquals("张三(28岁)", result);
        }
    }

    // ==================== 嵌套字段提取场景 ====================

    @Nested
    @DisplayName("嵌套字段提取场景")
    class NestedFieldExtractionTests {

        @Test
        @DisplayName("从 data.hisOrderId 提取处方ID")
        void extractFromNestedData() {
            Map<String, Object> innerData = new HashMap<>();
            innerData.put("hisOrderId", "35857670");
            innerData.put("patientId", "5023461");

            Map<String, Object> data = new HashMap<>();
            data.put("data", innerData);

            Object result = SpelUtils.parserPreNode("#data[data][hisOrderId]", data, null, null);

            assertEquals("35857670", result);
        }

        @Test
        @DisplayName("从多层嵌套提取字段")
        void extractFromDeepNested() {
            Map<String, Object> patientInfo = new HashMap<>();
            patientInfo.put("patientCode", "26011300006");

            Map<String, Object> body = new HashMap<>();
            body.put("patientInfo", patientInfo);

            Map<String, Object> data = new HashMap<>();
            data.put("body", body);

            Object result = SpelUtils.parserPreNode("#data[body][patientInfo][patientCode]", data, null, null);

            assertEquals("26011300006", result);
        }

        @Test
        @DisplayName("嵌套字段不存在时返回null")
        void extractNonExistentField() {
            Map<String, Object> data = new HashMap<>();
            data.put("body", new HashMap<>());

            Object result = SpelUtils.parserPreNode("#data[body][nonExistent]", data, null, null);

            assertNull(result);
        }
    }

    // ==================== 医技调度场景（scheduleExpr） ====================

    @Nested
    @DisplayName("医技调度场景")
    class ScheduleExpressionTests {

        @Test
        @DisplayName("根据检查类型调度到PACS节点")
        void scheduleByItemType_PACS() {
            Map<String, Object> data = new HashMap<>();
            data.put("itemType", "CT");

            List<Integer> result = SpelUtils.parseScheduleExpression(
                    "#data[itemType] == 'CT' || #data[itemType] == 'MR' ? T(java.util.Arrays).asList(2) : T(java.util.Arrays).asList(3)",
                    data);

            assertEquals(Arrays.asList(2), result);
        }

        @Test
        @DisplayName("根据检查类型调度到LIS节点")
        void scheduleByItemType_LIS() {
            Map<String, Object> data = new HashMap<>();
            data.put("itemType", "BLOOD");

            List<Integer> result = SpelUtils.parseScheduleExpression(
                    "#data[itemType] == 'CT' || #data[itemType] == 'MR' ? T(java.util.Arrays).asList(2) : T(java.util.Arrays).asList(3)",
                    data);

            assertEquals(Arrays.asList(3), result);
        }

        @Test
        @DisplayName("多节点调度：同时发送到PACS和LIS")
        void scheduleToMultipleNodes() {
            Map<String, Object> data = new HashMap<>();
            data.put("needBoth", true);

            List<Integer> result = SpelUtils.parseScheduleExpression(
                    "#data[needBoth] == true ? T(java.util.Arrays).asList(2, 3) : T(java.util.Arrays).asList(2)",
                    data);

            assertEquals(Arrays.asList(2, 3), result);
        }

        @Test
        @DisplayName("调度条件不满足时返回null")
        void scheduleConditionNotMet() {
            Map<String, Object> data = new HashMap<>();
            data.put("code", "500");

            List<Integer> result = SpelUtils.parseScheduleExpression(
                    "#data[code] == '200' ? T(java.util.Arrays).asList(2) : null", data);

            assertNull(result);
        }

        @Test
        @DisplayName("空表达式返回null")
        void emptyExpression() {
            List<Integer> result = SpelUtils.parseScheduleExpression("", new HashMap<>());
            assertNull(result);

            result = SpelUtils.parseScheduleExpression(null, new HashMap<>());
            assertNull(result);
        }

        @Test
        @DisplayName("纯空白表达式返回null")
        void whitespaceOnlyExpression() {
            List<Integer> result = SpelUtils.parseScheduleExpression("   ", new HashMap<>());
            assertNull(result);
        }

        @Test
        @DisplayName("返回空列表")
        void emptyListResult() {
            List<Integer> result = SpelUtils.parseScheduleExpression(
                    "T(java.util.Collections).emptyList()", new HashMap<>());
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("SpEL语法错误抛出 AgentConfigException")
        void spelSyntaxError() {
            assertThrows(AgentConfigException.class, () ->
                    SpelUtils.parseScheduleExpression("#data[", new HashMap<>()));
        }

        @Test
        @DisplayName("返回类型错误时抛出异常")
        void invalidReturnType() {
            assertThrows(AgentConfigException.class, () ->
                    SpelUtils.parseScheduleExpression("'not a list'", new HashMap<>()));
        }

        @Test
        @DisplayName("元素类型错误时抛出异常")
        void invalidElementType() {
            assertThrows(AgentConfigException.class, () ->
                    SpelUtils.parseScheduleExpression("T(java.util.Arrays).asList('a', 'b')", new HashMap<>()));
        }
    }

    // ==================== 后置处理场景（联合 INPUT 和 RESPONSE） ====================

    @Nested
    @DisplayName("后置处理场景")
    class PostProcessTests {

        @Test
        @DisplayName("从 RESPONSE 提取状态码并转换")
        void extractFromResponse() {
            Map<String, Object> response = new HashMap<>();
            response.put("returnCode", "1");
            response.put("errorMessage", "成功");

            Object result = SpelUtils.parserPostNode(
                    "#response[returnCode] == '1' ? '200' : '-1'", new HashMap<>(), null, response);

            assertEquals("200", result);
        }

        @Test
        @DisplayName("联合 INPUT 和 RESPONSE 处理")
        void combineInputAndResponse() {
            Map<String, Object> input = new HashMap<>();
            input.put("requestId", "REQ-001");

            Map<String, Object> response = new HashMap<>();
            response.put("resultCode", "SUCCESS");

            Object result = SpelUtils.parserPostNode(
                    "#input[requestId] + ':' + #response[resultCode]", new HashMap<>(), input, response);

            assertEquals("REQ-001:SUCCESS", result);
        }

        @Test
        @DisplayName("从 INPUT 获取原始请求信息")
        void extractFromInput() {
            Map<String, Object> input = new HashMap<>();
            input.put("orderId", "ORD-12345");

            Object result = SpelUtils.parserPostNode(
                    "#input[orderId]", new HashMap<>(), input, new HashMap<>());

            assertEquals("ORD-12345", result);
        }

        @Test
        @DisplayName("响应为空时使用默认值")
        void responseNullWithDefault() {
            Map<String, Object> response = new HashMap<>();
            response.put("message", null);

            Object result = SpelUtils.parserPostNode(
                    "#response[message] ?: '操作完成'", new HashMap<>(), null, response);

            assertEquals("操作完成", result);
        }
    }

    // ==================== 条件路由场景（routeExpr） ====================

    @Nested
    @DisplayName("条件路由场景")
    class RouteExpressionTests {

        @Test
        @DisplayName("根据科室路由到不同HIS")
        void routeByDepartment() {
            Map<String, Object> data = new HashMap<>();
            data.put("deptCode", "RADIOLOGY");

            Object result = SpelUtils.parserPreNode(
                    "#data[deptCode] == 'RADIOLOGY' ? 'PACS' : 'HIS'", data, null, null);

            assertEquals("PACS", result);
        }

        @Test
        @DisplayName("根据金额路由：大额走人工审核")
        void routeByAmount() {
            Map<String, Object> data = new HashMap<>();
            data.put("totalAmount", 10000);

            Object result = SpelUtils.parserPreNode(
                    "#data[totalAmount] > 5000 ? 'MANUAL_REVIEW' : 'AUTO_PROCESS'", data, null, null);

            assertEquals("MANUAL_REVIEW", result);
        }
    }

    // ==================== 基础功能测试 ====================

    @Nested
    @DisplayName("基础功能测试")
    class BasicFunctionTests {

        @Test
        @DisplayName("表达式语法校验：有效表达式")
        void validateSyntax_Valid() {
            assertTrue(SpelUtils.validateExpressionSyntax("1 + 1"));
            assertTrue(SpelUtils.validateExpressionSyntax("#data[name]"));
            assertTrue(SpelUtils.validateExpressionSyntax("#data[sex] == '1' ? '男' : '女'"));
        }

        @Test
        @DisplayName("表达式语法校验：无效表达式")
        void validateSyntax_Invalid() {
            assertFalse(SpelUtils.validateExpressionSyntax("1 + "));
            assertFalse(SpelUtils.validateExpressionSyntax("#data["));
        }

        @Test
        @DisplayName("使用 parent 变量访问父级数据")
        void accessParentData() {
            Map<String, Object> data = new HashMap<>();
            Map<String, Object> parent = new HashMap<>();
            parent.put("patientCode", "P001");

            Object result = SpelUtils.parserPreNode("#parent[patientCode]", data, parent, null);

            assertEquals("P001", result);
        }

        @Test
        @DisplayName("使用 source 变量访问源数据")
        void accessSourceData() {
            Map<String, Object> data = new HashMap<>();
            Map<String, Object> source = new HashMap<>();
            source.put("traceId", "TRACE-001");

            Object result = SpelUtils.parserPreNode("#source[traceId]", data, null, source);

            assertEquals("TRACE-001", result);
        }

        @Test
        @DisplayName("Bean 访问：前置处理 Bean 不存在时抛出异常")
        void beanAccessPreProcessBeanNotFound() {
            assertThrows(Exception.class, () ->
                    SpelUtils.parserPreNodeWithBeanAccess("@missingBean.toString()", new HashMap<>(), null, null));
        }

        @Test
        @DisplayName("Bean 访问：后置处理 Bean 不存在时抛出异常")
        void beanAccessPostProcessBeanNotFound() {
            assertThrows(Exception.class, () ->
                    SpelUtils.parserPostNodeWithBeanAccess("@missingBean.toString()", new HashMap<>(), null, null));
        }

        @Test
        @DisplayName("表达式解析异常时抛出异常")
        void expressionParseError() {
            assertThrows(Exception.class, () ->
                    SpelUtils.parserPreNode("#data[nonExistent].unknownMethod()", new HashMap<>(), null, null));
        }
    }

    // ==================== Bean表达式前置处理场景 ====================

    @Nested
    @DisplayName("Bean表达式前置处理场景")
    class BeanExpressionPreProcessTests {

        @Test
        @DisplayName("前置处理：使用 #data 访问当前数据")
        void preProcessWithData() {
            Map<String, Object> data = new HashMap<>();
            data.put("visitNo", "V001");

            // 由于无法访问真实Bean，测试表达式语法解析和上下文变量设置
            Object result = SpelUtils.parserPreNodeWithBeanAccess("#data[visitNo]", data, null, null);

            assertEquals("V001", result);
        }

        @Test
        @DisplayName("前置处理：使用 #parent 访问父级数据")
        void preProcessWithParent() {
            Map<String, Object> data = new HashMap<>();
            Map<String, Object> parent = new HashMap<>();
            parent.put("patientCode", "P001");

            Object result = SpelUtils.parserPreNodeWithBeanAccess("#parent[patientCode]", data, parent, null);

            assertEquals("P001", result);
        }

        @Test
        @DisplayName("前置处理：使用 #source 访问源数据")
        void preProcessWithSource() {
            Map<String, Object> data = new HashMap<>();
            Map<String, Object> source = new HashMap<>();
            source.put("traceId", "TRACE-001");

            Object result = SpelUtils.parserPreNodeWithBeanAccess("#source[traceId]", data, null, source);

            assertEquals("TRACE-001", result);
        }

        @Test
        @DisplayName("前置处理：Bean不存在时抛出异常")
        void preProcessBeanNotFound() {
            assertThrows(Exception.class, () ->
                    SpelUtils.parserPreNodeWithBeanAccess("@nonExistentBean.method()", new HashMap<>(), null, null));
        }

        @Test
        @DisplayName("前置处理：表达式解析异常时抛出异常")
        void preProcessExpressionError() {
            assertThrows(Exception.class, () ->
                    SpelUtils.parserPreNodeWithBeanAccess("#data[invalid", new HashMap<>(), null, null));
        }
    }

    // ==================== Bean表达式后置处理场景 ====================

    @Nested
    @DisplayName("Bean表达式后置处理场景")
    class BeanExpressionPostProcessTests {

        @Test
        @DisplayName("后置处理：使用 #data 访问当前数据")
        void postProcessWithData() {
            Map<String, Object> data = new HashMap<>();
            data.put("recordId", "R001");

            Object result = SpelUtils.parserPostNodeWithBeanAccess("#data[recordId]", data, null, null);

            assertEquals("R001", result);
        }

        @Test
        @DisplayName("后置处理：使用 #input 访问输入数据")
        void postProcessWithInput() {
            Map<String, Object> data = new HashMap<>();
            Map<String, Object> input = new HashMap<>();
            input.put("requestId", "REQ-001");

            Object result = SpelUtils.parserPostNodeWithBeanAccess("#input[requestId]", data, input, null);

            assertEquals("REQ-001", result);
        }

        @Test
        @DisplayName("后置处理：使用 #response 访问响应数据")
        void postProcessWithResponse() {
            Map<String, Object> data = new HashMap<>();
            Map<String, Object> response = new HashMap<>();
            response.put("resultCode", "SUCCESS");

            Object result = SpelUtils.parserPostNodeWithBeanAccess("#response[resultCode]", data, null, response);

            assertEquals("SUCCESS", result);
        }

        @Test
        @DisplayName("后置处理：联合 #input 和 #response 处理")
        void postProcessCombineInputAndResponse() {
            Map<String, Object> data = new HashMap<>();
            Map<String, Object> input = new HashMap<>();
            input.put("orderId", "ORD-001");
            Map<String, Object> response = new HashMap<>();
            response.put("status", "COMPLETED");

            Object result = SpelUtils.parserPostNodeWithBeanAccess(
                    "#input[orderId] + '-' + #response[status]", data, input, response);

            assertEquals("ORD-001-COMPLETED", result);
        }

        @Test
        @DisplayName("后置处理：Bean不存在时抛出异常")
        void postProcessBeanNotFound() {
            assertThrows(Exception.class, () ->
                    SpelUtils.parserPostNodeWithBeanAccess("@nonExistentBean.method()", new HashMap<>(), null, null));
        }

        @Test
        @DisplayName("后置处理：表达式解析异常时抛出异常")
        void postProcessExpressionError() {
            assertThrows(Exception.class, () ->
                    SpelUtils.parserPostNodeWithBeanAccess("#data[invalid", new HashMap<>(), null, null));
        }
    }

    // ==================== 节点ID提取场景（extractNodeIdsFromScheduleExpr） ====================

    @Nested
    @DisplayName("节点ID提取场景")
    class ExtractNodeIdsTests {

        @Test
        @DisplayName("单分支表达式提取节点ID")
        void extractSingleBranch() {
            List<Integer> ids = SpelUtils.extractNodeIdsFromScheduleExpr(
                    "T(java.util.Arrays).asList(39)");
            assertEquals(Arrays.asList(39), ids);
        }

        @Test
        @DisplayName("多分支表达式提取所有节点ID")
        void extractMultipleBranches() {
            List<Integer> ids = SpelUtils.extractNodeIdsFromScheduleExpr(
                    "#data[code] == '-1' ? T(java.util.Arrays).asList(39) : T(java.util.Arrays).asList(40, 41)");
            assertEquals(Arrays.asList(39, 40, 41), ids);
        }

        @Test
        @DisplayName("单个asList中多个节点")
        void extractMultipleNodesInOneList() {
            List<Integer> ids = SpelUtils.extractNodeIdsFromScheduleExpr(
                    "T(java.util.Arrays).asList(2, 3, 5)");
            assertEquals(Arrays.asList(2, 3, 5), ids);
        }

        @Test
        @DisplayName("null表达式返回空列表")
        void extractFromNull() {
            List<Integer> ids = SpelUtils.extractNodeIdsFromScheduleExpr(null);
            assertTrue(ids.isEmpty());
        }

        @Test
        @DisplayName("空字符串返回空列表")
        void extractFromEmpty() {
            List<Integer> ids = SpelUtils.extractNodeIdsFromScheduleExpr("");
            assertTrue(ids.isEmpty());
        }

        @Test
        @DisplayName("纯空白字符串返回空列表")
        void extractFromWhitespace() {
            List<Integer> ids = SpelUtils.extractNodeIdsFromScheduleExpr("   ");
            assertTrue(ids.isEmpty());
        }

        @Test
        @DisplayName("无asList模式的表达式返回空列表")
        void extractFromNoAsListPattern() {
            List<Integer> ids = SpelUtils.extractNodeIdsFromScheduleExpr("#data[code] == '200'");
            assertTrue(ids.isEmpty());
        }

        @Test
        @DisplayName("相同节点ID出现在不同分支")
        void extractDuplicateNodeIds() {
            List<Integer> ids = SpelUtils.extractNodeIdsFromScheduleExpr(
                    "#data[x] ? T(java.util.Arrays).asList(2) : T(java.util.Arrays).asList(2, 3)");
            // extractNodeIdsFromScheduleExpr 不做去重，两个分支都有 2
            assertEquals(Arrays.asList(2, 2, 3), ids);
        }
    }
}
