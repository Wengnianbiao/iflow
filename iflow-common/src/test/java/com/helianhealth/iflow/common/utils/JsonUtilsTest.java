package com.helianhealth.iflow.common.utils;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonUtils 测试类
 * 覆盖业务场景：序列化、反序列化、类型转换、深拷贝、metaInfo解析
 */
public class JsonUtilsTest {

    // ==================== 序列化场景 ====================

    @Nested
    @DisplayName("序列化场景")
    class SerializationTests {

        @Test
        @DisplayName("Map序列化为JSON字符串")
        void toJsonString_FromMap() {
            Map<String, Object> data = new HashMap<>();
            data.put("patientCode", "P001");
            data.put("name", "张三");
            data.put("age", 28);

            String json = JsonUtils.toJsonString(data);

            assertNotNull(json);
            assertTrue(json.contains("\"patientCode\":\"P001\""));
            assertTrue(json.contains("\"name\":\"张三\""));
        }

        @Test
        @DisplayName("嵌套Map序列化")
        void toJsonString_NestedMap() {
            Map<String, Object> patientInfo = new HashMap<>();
            patientInfo.put("code", "P001");
            patientInfo.put("name", "张三");

            Map<String, Object> data = new HashMap<>();
            data.put("patientInfo", patientInfo);
            data.put("status", "ACTIVE");

            String json = JsonUtils.toJsonString(data);

            assertTrue(json.contains("patientInfo"));
            assertTrue(json.contains("\"code\":\"P001\""));
        }

        @Test
        @DisplayName("包含数组的Map序列化")
        void toJsonString_WithList() {
            Map<String, Object> data = new HashMap<>();
            data.put("orderId", "ORD001");
            data.put("items", Arrays.asList("item1", "item2", "item3"));

            String json = JsonUtils.toJsonString(data);

            assertTrue(json.contains("[\"item1\",\"item2\",\"item3\"]"));
        }

        @Test
        @DisplayName("空Map序列化")
        void toJsonString_EmptyMap() {
            String json = JsonUtils.toJsonString(new HashMap<>());
            assertEquals("{}", json);
        }

        @Test
        @DisplayName("null序列化")
        void toJsonString_Null() {
            String json = JsonUtils.toJsonString(null);
            assertEquals("null", json);
        }

        @Test
        @DisplayName("Map中的null值序列化时会省略对应key")
        void toJsonString_MapWithNullValue() {
            Map<String, Object> data = new HashMap<>();
            data.put("code", "200");
            data.put("message", null);

            String json = JsonUtils.toJsonString(data);
            assertTrue(json.contains("\"code\":\"200\""));
            assertFalse(json.contains("\"message\""));
        }

        @Test
        @DisplayName("Map中的null值序列化时保留key")
        void toJsonString_MapKeepNullValue() {
            Map<String, Object> data = new HashMap<>();
            data.put("code", "200");
            data.put("message", null);

            String json = JsonUtils.toJsonStringWithNulls(data);
            assertNotNull(json);
            assertTrue(json.contains("\"code\":\"200\""));
            assertTrue(json.contains("\"message\":null"));

            Map<String, Object> result = JsonUtils.parseObjectMap(json);
            assertTrue(result.containsKey("message"));
            assertNull(result.get("message"));
            assertEquals("200", result.get("code"));
        }
    }

    // ==================== 反序列化场景 ====================

    @Nested
    @DisplayName("反序列化场景")
    class DeserializationTests {

        @Test
        @DisplayName("JSON字符串反序列化为Map<String, String>")
        void fromJsonStringToMap_Normal() {
            String json = "{\"patientCode\":\"P001\",\"name\":\"张三\",\"sex\":\"1\"}";

            Map<String, String> result = JsonUtils.parseStringMap(json);

            assertEquals("P001", result.get("patientCode"));
            assertEquals("张三", result.get("name"));
            assertEquals("1", result.get("sex"));
        }

        @Test
        @DisplayName("空JSON对象反序列化")
        void fromJsonStringToMap_Empty() {
            Map<String, String> result = JsonUtils.parseStringMap("{}");

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("null输入抛出异常")
        void fromJsonStringToMap_Null() {
            assertThrows(RuntimeException.class, () ->
                    JsonUtils.parseStringMap(null));
        }

        @Test
        @DisplayName("无效JSON抛出异常")
        void fromJsonStringToMap_Invalid() {
            assertThrows(RuntimeException.class, () ->
                    JsonUtils.parseStringMap("not a json"));
        }

        @Test
        @DisplayName("JSON字符串反序列化为Map<String, Object>")
        void fromJsonStringToObjectMap_Normal() {
            String json = "{\"code\":\"200\",\"count\":10,\"success\":true}";

            Map<String, Object> result = JsonUtils.parseObjectMap(json);

            assertEquals("200", result.get("code"));
            assertEquals(10, result.get("count"));
            assertEquals(true, result.get("success"));
        }

        @Test
        @DisplayName("包含嵌套对象的JSON反序列化")
        void fromJsonStringToObjectMap_Nested() {
            String json = "{\"data\":{\"hisOrderId\":\"12345\",\"patientId\":\"P001\"}}";

            Map<String, Object> result = JsonUtils.parseObjectMap(json);

            assertInstanceOf(Map.class, result.get("data"));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertEquals("12345", data.get("hisOrderId"));
        }

        @Test
        @DisplayName("包含数组的JSON反序列化")
        void fromJsonStringToObjectMap_WithArray() {
            String json = "{\"items\":[\"a\",\"b\",\"c\"]}";

            Map<String, Object> result = JsonUtils.parseObjectMap(json);

            assertInstanceOf(List.class, result.get("items"));
            @SuppressWarnings("unchecked")
            List<String> items = (List<String>) result.get("items");
            assertEquals(3, items.size());
            assertEquals("a", items.get(0));
        }
    }

    @Nested
    @DisplayName("对象转换场景")
    class ObjectConversionTests {

        @Test
        @DisplayName("Bean转换为Map")
        void fromBean_Normal() {
            DemoBean bean = new DemoBean();
            bean.setPatientCode("P001");
            bean.setPatientName("张三");

            Map<String, Object> result = JsonUtils.fromBean(bean);

            assertNotNull(result);
            assertEquals("P001", result.get("patientCode"));
            assertEquals("张三", result.get("patientName"));
        }

        @Test
        @DisplayName("null Bean返回null")
        void fromBean_Null() {
            assertNull(JsonUtils.fromBean(null));
        }

        @Test
        @DisplayName("JSONObject转换为新Map")
        void fromJsonObject_Normal() {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("code", "200");
            jsonObject.put("message", "成功");

            Map<String, Object> result = JsonUtils.fromJsonObject(jsonObject);

            assertNotNull(result);
            assertEquals("200", result.get("code"));
            assertEquals("成功", result.get("message"));
            assertNotSame(jsonObject, result);

            result.put("extra", "value");
            assertFalse(jsonObject.containsKey("extra"));
        }

        @Test
        @DisplayName("null JSONObject返回null")
        void fromJsonObject_Null() {
            assertNull(JsonUtils.fromJsonObject(null));
        }
    }

    // ==================== 深拷贝场景 ====================

    @Nested
    @DisplayName("深拷贝场景")
    class DeepCopyTests {

        @Test
        @DisplayName("简单Map深拷贝")
        void deepCopyMap_Simple() {
            Map<String, Object> original = new HashMap<>();
            original.put("name", "张三");
            original.put("age", 28);

            Map<String, Object> copy = JsonUtils.deepCopyMap(original);

            // 验证内容相同
            assertEquals("张三", copy.get("name"));
            assertEquals(28, copy.get("age"));

            // 验证是不同对象
            assertNotSame(original, copy);

            // 修改副本不影响原始
            copy.put("name", "李四");
            assertEquals("张三", original.get("name"));
        }

        @Test
        @DisplayName("嵌套Map深拷贝")
        void deepCopyMap_Nested() {
            Map<String, Object> inner = new HashMap<>();
            inner.put("code", "P001");

            Map<String, Object> original = new HashMap<>();
            original.put("patient", inner);

            Map<String, Object> copy = JsonUtils.deepCopyMap(original);

            // 修改副本的嵌套对象不影响原始
            @SuppressWarnings("unchecked")
            Map<String, Object> copiedInner = (Map<String, Object>) copy.get("patient");
            copiedInner.put("code", "P002");

            // 原始嵌套对象未变
            @SuppressWarnings("unchecked")
            Map<String, Object> originalInner = (Map<String, Object>) original.get("patient");
            assertEquals("P001", originalInner.get("code"));
        }

        @Test
        @DisplayName("包含数组的Map深拷贝")
        void deepCopyMap_WithList() {
            Map<String, Object> original = new HashMap<>();
            original.put("items", Arrays.asList("a", "b", "c"));

            Map<String, Object> copy = JsonUtils.deepCopyMap(original);

            // 验证数组内容相同
            @SuppressWarnings("unchecked")
            List<String> copiedItems = (List<String>) copy.get("items");
            assertEquals(3, copiedItems.size());
            assertEquals("a", copiedItems.get(0));
        }

        @Test
        @DisplayName("null深拷贝返回null")
        void deepCopyMap_Null() {
            Map<String, Object> result = JsonUtils.deepCopyMap(null);
            assertNull(result);
        }
    }

    // ==================== metaInfo解析场景 ====================

    @Nested
    @DisplayName("metaInfo解析场景")
    class MetaInfoParseTests {

        @Test
        @DisplayName("解析HTTP节点metaInfo")
        void parseMetaInfo_HttpNode() {
            String metaInfo = "{\"url\":\"http://his.example.com/api\",\"method\":\"POST\",\"contentType\":\"application/json\"}";

            Map<String, Object> result = JsonUtils.parseMetaInfo(metaInfo);

            assertEquals("http://his.example.com/api", result.get("url"));
            assertEquals("POST", result.get("method"));
            assertEquals("application/json", result.get("contentType"));
        }

        @Test
        @DisplayName("解析WebService节点metaInfo")
        void parseMetaInfo_WebServiceNode() {
            String metaInfo = "{\"wsdlUrl\":\"http://his.example.com/ws?wsdl\",\"operationName\":\"CreateArchives\"}";

            Map<String, Object> result = JsonUtils.parseMetaInfo(metaInfo);

            assertEquals("http://his.example.com/ws?wsdl", result.get("wsdlUrl"));
            assertEquals("CreateArchives", result.get("operationName"));
        }

        @Test
        @DisplayName("解析包含headers的metaInfo")
        void parseMetaInfo_WithHeaders() {
            String metaInfo = "{\"url\":\"http://api.example.com\",\"headers\":{\"Authorization\":\"Bearer xxx\",\"X-Sign\":\"abc123\"}}";

            Map<String, Object> result = JsonUtils.parseMetaInfo(metaInfo);

            assertInstanceOf(Map.class, result.get("headers"));
            @SuppressWarnings("unchecked")
            Map<String, Object> headers = (Map<String, Object>) result.get("headers");
            assertEquals("Bearer xxx", headers.get("Authorization"));
        }

        @Test
        @DisplayName("空metaInfo返回空Map")
        void parseMetaInfo_Empty() {
            Map<String, Object> result1 = JsonUtils.parseMetaInfo(null);
            Map<String, Object> result2 = JsonUtils.parseMetaInfo("");

            assertNotNull(result1);
            assertTrue(result1.isEmpty());
            assertNotNull(result2);
            assertTrue(result2.isEmpty());
        }

    }

    // ==================== 业务数据处理场景 ====================

    @Nested
    @DisplayName("业务数据处理场景")
    class BusinessDataTests {

        @Test
        @DisplayName("处理HIS响应数据")
        void processHisResponse() {
            String hisResponse = "{\"returnCode\":\"1\",\"errorMessage\":\"成功\",\"data\":{\"hisPatientId\":\"HIS001\"}}";

            Map<String, Object> result = JsonUtils.parseObjectMap(hisResponse);

            assertEquals("1", result.get("returnCode"));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertEquals("HIS001", data.get("hisPatientId"));
        }

        @Test
        @DisplayName("处理收费通知数据")
        void processChargeNotification() {
            String notification = "{\"chargeType\":\"CHARGED\",\"data\":{\"hisOrderId\":\"35857670\",\"patientId\":\"5023461\"}}";

            Map<String, Object> result = JsonUtils.parseObjectMap(notification);

            assertEquals("CHARGED", result.get("chargeType"));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertEquals("35857670", data.get("hisOrderId"));
        }

        @Test
        @DisplayName("处理账单明细数组")
        void processOrderDetails() {
            String orderJson = "{\"orderId\":\"ORD001\",\"orderDetails\":[{\"itemId\":\"I001\",\"price\":100},{\"itemId\":\"I002\",\"price\":200}]}";

            Map<String, Object> result = JsonUtils.parseObjectMap(orderJson);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("orderDetails");
            assertEquals(2, details.size());
            assertEquals("I001", details.get(0).get("itemId"));
            assertEquals(100, details.get(0).get("price"));
        }
    }

    @Test
    @DisplayName("parseObjectMap在null输入时抛出异常")
    void parseObjectMap_Null() {
        assertThrows(IllegalArgumentException.class, () -> JsonUtils.parseObjectMap(null));
    }

    @Test
    @DisplayName("parseObjectMap在非法JSON输入时抛出异常")
    void parseObjectMap_Invalid() {
        assertThrows(IllegalArgumentException.class, () -> JsonUtils.parseObjectMap("not a json"));
    }

    @Test
    @DisplayName("parseMetaInfo在非法JSON输入时抛出异常")
    void parseMetaInfo_Invalid() {
        assertThrows(IllegalArgumentException.class, () -> JsonUtils.parseMetaInfo("{invalid}"));
    }

    @SuppressWarnings("unused")
    private static class DemoBean {
        private String patientCode;
        private String patientName;

        public String getPatientCode() {
            return patientCode;
        }

        public void setPatientCode(String patientCode) {
            this.patientCode = patientCode;
        }

        public String getPatientName() {
            return patientName;
        }

        public void setPatientName(String patientName) {
            this.patientName = patientName;
        }
    }
}
