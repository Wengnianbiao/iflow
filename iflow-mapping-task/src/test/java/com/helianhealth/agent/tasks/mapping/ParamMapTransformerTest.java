package com.helianhealth.agent.tasks.mapping;

import com.helianhealth.agent.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.agent.tasks.mapping.enums.MappingType;
import com.helianhealth.agent.tasks.mapping.enums.ParamType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParamMapTransformerTest {
    private final ParamMapTransformer transformer = new ParamMapTransformer(new GraalJSEvaluator());

    @Test
    void objectAndExpressionAlwaysReadOriginalPayloadRegardlessOfFieldOrder() {
        var first = constant("code", ParamType.STRING, "mapped");
        var second = expression("message", ParamType.STRING, "$.code");
        var nested = object("patient", name("id", ParamType.STRING, "$.id"));
        var payload = Map.of("code", "original", "id", "P1");
        assertEquals(Map.of("code", "mapped", "message", "original", "patient", Map.of("id", "P1")),
                transformer.transform(List.of(first, second, nested), payload));
        assertEquals(transformer.transform(List.of(first, second), payload),
                transformer.transform(List.of(second, first), payload));
        assertEquals("original", payload.get("code"));
    }

    @Test
    void singleFieldObjectElementsStayObjectsAndCanReadPayload() {
        var loop = loop("details", "$.items", "item", object(null, name("code", ParamType.STRING, "item.code")));
        var payload = Map.of("patientId", "P1", "items", List.of(Map.of("code", "A"), Map.of("code", "B")));
        assertEquals(List.of(Map.of("code", "A"), Map.of("code", "B")), map(loop, payload));
        loop.getItemTemplate().setChildParams(List.of(
                name("code", ParamType.STRING, "item.code"), name("patientId", ParamType.STRING, "$.patientId"),
                constant("enabled", ParamType.BOOLEAN, true)));
        assertEquals(List.of(Map.of("code", "A", "patientId", "P1", "enabled", true),
                Map.of("code", "B", "patientId", "P1", "enabled", true)), map(loop, payload));
    }

    @Test
    void scalarAndRootArraysHaveExplicitElementTemplates() {
        assertEquals(List.of(2, 3), map(loop("values", "$", "item",
                expression(null, ParamType.INTEGER, "Number(item) + 1")), List.of(1, 2)));
        assertEquals(List.of("A", "B"), map(loop("codes", "$.items", "item",
                name(null, ParamType.STRING, "item.code")), Map.of("items", List.of(Map.of("code", "A"), Map.of("code", "B")))));
        assertEquals("hello", map(expression("value", ParamType.STRING, "$"), "hello"));
        assertEquals(2, map(name("value", ParamType.INTEGER, "$[1]"), List.of(1, 2)));
    }

    @Test
    void nestedLoopsCanReadRootOuterAndInnerVariables() {
        var inner = loop("lines", "order.lines", "line", object(null,
                name("patient", ParamType.STRING, "$.patientId"),
                name("order", ParamType.STRING, "order.id"),
                expression("code", ParamType.STRING, "order.id + ':' + line.code")));
        var outer = loop("orders", "$.orders", "order", object(null, inner));
        var payload = Map.of("patientId", "P1", "orders", List.of(
                Map.of("id", "O1", "lines", List.of(Map.of("code", "A"))),
                Map.of("id", "O2", "lines", List.of(Map.of("code", "B")))));
        assertEquals(List.of(Map.of("lines", List.of(Map.of("patient", "P1", "order", "O1", "code", "O1:A"))),
                Map.of("lines", List.of(Map.of("patient", "P1", "order", "O2", "code", "O2:B")))), map(outer, payload));
        assertEquals("undefined", map(expression("outside", ParamType.STRING, "typeof order"), payload));
    }

    @Test
    void fixedArraysPreserveCountNullsOrderAndNestedArrays() {
        var fixed = fixed("items", object(null, name("id", ParamType.STRING, "$.id")),
                constant(null, ParamType.INTEGER, 7), constant(null, ParamType.STRING, null),
                fixed(null, constant(null, ParamType.BOOLEAN, true)));
        assertEquals(Arrays.asList(Map.of("id", "P1"), 7, null, List.of(true)), map(fixed, Map.of("id", "P1")));
        assertEquals(List.of(Map.of("id", "P1")), map(fixed("single", object(null,
                name("id", ParamType.STRING, "$.id"))), Map.of("id", "P1")));
        assertEquals(List.of(), map(fixed("empty"), Map.of()));
    }

    @Test
    void nativeConstantsAndExpressionResultsPreserveJsonTypes() {
        assertEquals(Map.of("a", 1), map(expression("out", ParamType.OBJECT, "({a: 1})"), Map.of()));
        assertEquals(List.of(1, 2), map(expression("out", ParamType.ARRAY, "[1, 2]"), Map.of()));
        assertEquals(12, map(constant("out", ParamType.INTEGER, "12"), Map.of()));
        assertEquals("  keep spaces  ", map(constant("out", ParamType.STRING, "  keep spaces  "), Map.of()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void expressionCanPassThroughObjectsWithoutSharingPayloadState() {
        var patient = new LinkedHashMap<String, Object>();
        patient.put("id", "P1");
        var payload = new LinkedHashMap<String, Object>();
        payload.put("patient", patient);

        var copiedPatient = (Map<String, Object>) map(expression("patient", ParamType.OBJECT, "$.patient"), payload);

        assertEquals(Map.of("id", "P1"), copiedPatient);
        assertNotSame(patient, copiedPatient);

        copiedPatient.put("id", "changed");
        assertEquals("P1", patient.get("id"));
    }

    @Test
    void expressionsCannotMutatePayloadAliasesOrEarlierOutputs() {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("value", "original");
        payload.put("items", List.of(new LinkedHashMap<>(Map.of("value", "original"))));
        var loop = loop("rows", "$.items", "item", object(null,
                expression("changed", ParamType.STRING, "item.value = 'changed'; $.value = 'changed'; item.value"),
                name("stillOriginal", ParamType.STRING, "item.value")));
        var result = transformer.transform(List.of(loop, name("root", ParamType.STRING, "$.value")), payload);
        assertEquals("original", result.get("root"));
        assertEquals(List.of(Map.of("changed", "changed", "stillOriginal", "original")), result.get("rows"));
        assertEquals("original", payload.get("value"));
    }

    @Test
    void missingAndNullValuesRemainNullWhileEmptyLoopSourcesProduceEmptyArrays() {
        var loop = loop("rows", "$.items", "item", name(null, ParamType.STRING, "item"));
        assertEquals(List.of(), map(loop, Map.of()));
        assertEquals(List.of(), map(loop, Map.of("items", List.of())));
        var payload = new LinkedHashMap<String, Object>();
        payload.put("items", null);
        assertEquals(List.of(), map(loop, payload));
        assertNull(map(name("out", ParamType.STRING, "$.missing.nested"), Map.of()));
        assertEquals(Arrays.asList(null, "A"), map(loop("values", "$", "item", name(null, ParamType.STRING, "item")),
                Arrays.asList(null, "A")));
    }

    @Test
    void errorsContainOutputPathAndActualArrayIndex() {
        var loop = loop("details", "$", "item", object(null, name("quantity", ParamType.INTEGER, "item.qty")));
        var failure = assertThrows(IllegalArgumentException.class, () -> map(loop,
                List.of(Map.of("qty", "1"), Map.of("qty", "bad"))));
        assertTrue(failure.getMessage().contains("$.details[1].quantity"), failure.getMessage());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> map(loop, Map.of())).getMessage().contains("循环源必须为数组"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> map(expression("value", ParamType.ARRAY, "'wrong'"), Map.of()))
                .getMessage().contains("$.value"));
    }

    @Test
    void invalidTemplatesAndAliasesAreRejectedEvenForEmptySources() {
        for (String alias : List.of("$", "XML", "Crypto", "JSON", "for", "bad-name")) {
            assertThrows(IllegalArgumentException.class, () -> map(loop("rows", "$", alias,
                    constant(null, ParamType.STRING, "a")), List.of()));
        }
        var nested = loop("rows", "$", "item", loop(null, "item.rows", "item", object(null)));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> map(nested, List.of())).getMessage().contains("重名"));
        assertThrows(IllegalArgumentException.class, () -> map(loop("rows", "$", "item", null), List.of()));
        assertThrows(IllegalArgumentException.class, () -> map(loop("rows", "$", "item",
                name("unexpectedKey", ParamType.STRING, "item")), List.of()));
        assertThrows(IllegalArgumentException.class, () -> map(loop("rows", "$", "item",
                name(null, ParamType.STRING, "other.code")), List.of()));
    }

    @Test
    void invalidModesPathsAndDuplicateFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> map(name("out", ParamType.STRING, "id"), Map.of("id", "P1")));
        assertThrows(IllegalArgumentException.class, () -> map(name("out", ParamType.STRING, "$.items["), Map.of()));
        var objectNameFailure = assertThrows(IllegalArgumentException.class,
                () -> map(name("out", ParamType.OBJECT, "$.object"), Map.of("object", Map.of())));
        assertTrue(objectNameFailure.getMessage().contains("$.out"), objectNameFailure.getMessage());
        assertTrue(objectNameFailure.getMessage().contains("对象仅支持对象构建或 JS 表达式"),
                objectNameFailure.getMessage());
        var objectConstantFailure = assertThrows(IllegalArgumentException.class,
                () -> map(constant("out", ParamType.OBJECT, Map.of()), Map.of()));
        assertTrue(objectConstantFailure.getMessage().contains("$.out"), objectConstantFailure.getMessage());
        assertTrue(objectConstantFailure.getMessage().contains("对象仅支持对象构建或 JS 表达式"),
                objectConstantFailure.getMessage());
        var nestedObjectNameFailure = assertThrows(IllegalArgumentException.class,
                () -> map(fixed("outer", name(null, ParamType.OBJECT, "$.patient")), Map.of("patient", Map.of())));
        assertTrue(nestedObjectNameFailure.getMessage().contains("$.outer[0]"), nestedObjectNameFailure.getMessage());
        assertTrue(nestedObjectNameFailure.getMessage().contains("对象仅支持对象构建或 JS 表达式"),
                nestedObjectNameFailure.getMessage());
        var arrayNameFailure = assertThrows(IllegalArgumentException.class,
                () -> map(name("out", ParamType.ARRAY, "$.items"), Map.of("items", List.of())));
        assertTrue(arrayNameFailure.getMessage().contains("$.out"), arrayNameFailure.getMessage());
        assertTrue(arrayNameFailure.getMessage().contains("数组仅支持循环映射、固定长度或 JS 表达式"),
                arrayNameFailure.getMessage());
        var arrayConstantFailure = assertThrows(IllegalArgumentException.class,
                () -> map(constant("out", ParamType.ARRAY, List.of()), Map.of()));
        assertTrue(arrayConstantFailure.getMessage().contains("$.out"), arrayConstantFailure.getMessage());
        assertTrue(arrayConstantFailure.getMessage().contains("数组仅支持循环映射、固定长度或 JS 表达式"),
                arrayConstantFailure.getMessage());
        var nestedArrayNameFailure = assertThrows(IllegalArgumentException.class,
                () -> map(fixed("outer", name(null, ParamType.ARRAY, "$.items")), Map.of("items", List.of())));
        assertTrue(nestedArrayNameFailure.getMessage().contains("$.outer[0]"), nestedArrayNameFailure.getMessage());
        assertTrue(nestedArrayNameFailure.getMessage().contains("数组仅支持循环映射、固定长度或 JS 表达式"),
                nestedArrayNameFailure.getMessage());
        var oldObject = name("out", ParamType.OBJECT, "$");
        oldObject.setChildParams(List.of(name("id", ParamType.STRING, "$.id")));
        assertThrows(IllegalArgumentException.class, () -> map(oldObject, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> transformer.transform(List.of(
                constant("id", ParamType.STRING, "a"), constant("id", ParamType.STRING, "b")), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> map(expression("out", ParamType.STRING, ""), Map.of()));
        var invalid = object("out");
        invalid.setTargetParamType(ParamType.STRING);
        assertThrows(IllegalArgumentException.class, () -> map(invalid, Map.of()));
    }

    private Object map(ParamMappingConfig config, Object payload) {
        return transformer.transform(List.of(config), payload).get(config.getTargetParamKey());
    }

    private static ParamMappingConfig name(String key, ParamType type, String source) {
        return ParamMappingConfig.builder().targetParamKey(key).targetParamType(type)
                .mappingType(MappingType.NAME).sourceParamKey(source).build();
    }

    private static ParamMappingConfig constant(String key, ParamType type, Object value) {
        return ParamMappingConfig.builder().targetParamKey(key).targetParamType(type)
                .mappingType(MappingType.CONSTANT).mappingRule(value).build();
    }

    private static ParamMappingConfig expression(String key, ParamType type, String expression) {
        return ParamMappingConfig.builder().targetParamKey(key).targetParamType(type)
                .mappingType(MappingType.EXPRESSION).mappingRule(expression).build();
    }

    private static ParamMappingConfig object(String key, ParamMappingConfig... children) {
        return ParamMappingConfig.builder().targetParamKey(key).targetParamType(ParamType.OBJECT)
                .mappingType(MappingType.OBJECT).childParams(List.of(children)).build();
    }

    private static ParamMappingConfig loop(String key, String source, String alias, ParamMappingConfig template) {
        return ParamMappingConfig.builder().targetParamKey(key).targetParamType(ParamType.ARRAY)
                .mappingType(MappingType.ARRAY_MAP).sourceParamKey(source).itemAlias(alias).itemTemplate(template).build();
    }

    private static ParamMappingConfig fixed(String key, ParamMappingConfig... items) {
        return ParamMappingConfig.builder().targetParamKey(key).targetParamType(ParamType.ARRAY)
                .mappingType(MappingType.ARRAY_FIXED).items(List.of(items)).build();
    }
}
