package com.helianhealth.iflow.tasks.mapping;

import com.helianhealth.iflow.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.iflow.tasks.mapping.enums.MappingType;
import com.helianhealth.iflow.tasks.mapping.enums.ParamType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

    @ParameterizedTest
    @MethodSource("definitePaths")
    void readsQuotedUnicodeAndIndexedPaths(String path, Object expected) {
        var payload = Map.of("field.name", "dotted", "患者", Map.of("姓名", "张三"),
                "groups", List.of(List.of(Map.of("code", "A")), List.of(Map.of("code", "B"))));

        assertEquals(expected, map(name("value", ParamType.STRING, path), payload));
    }

    private static Stream<Arguments> definitePaths() {
        return Stream.of(
                Arguments.of("$['field.name']", "dotted"),
                Arguments.of("$[\"field.name\"]", "dotted"),
                Arguments.of("$.患者.姓名", "张三"),
                Arguments.of("$['患者'][\"姓名\"]", "张三"),
                Arguments.of("$.groups[1][0].code", "B"),
                Arguments.of("$.groups[0][0]['code']", "A"),
                Arguments.of("$.missing", null),
                Arguments.of("$.missing.nested", null),
                Arguments.of("$.groups[2][0].code", null),
                Arguments.of("$.groups[0][1].code", null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "code", "$.groups[", "$.groups[-1]", "$.groups[0:1]",
            "$.groups[*]", "$.groups[?(@.code == 'A')]", "$..code", "$.groups[0,1]", "$.groups[code]"})
    void rejectsInvalidAndIndefinitePaths(String path) {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> map(name("value", ParamType.STRING, path), Map.of()));

        assertTrue(failure.getMessage().contains("MAPPING $.value"), failure.getMessage());
    }

    @Test
    void quotedAliasPathsReadCurrentElement() {
        var config = loop("rows", "$.items", "item", object(null,
                name("code", ParamType.STRING, "item['field.name']"),
                name("name", ParamType.STRING, "item[\"患者\"].姓名")));

        assertEquals(List.of(Map.of("code", "A", "name", "张三")), map(config,
                Map.of("items", List.of(Map.of("field.name", "A", "患者", Map.of("姓名", "张三"))))));
    }

    @Test
    void nestedObjectsPreserveFieldOrderAndAllowSameKeysInDifferentObjects() {
        var config = object("patient", constant("id", ParamType.STRING, "P1"),
                object("address", constant("id", ParamType.STRING, "A1"),
                        constant("city", ParamType.STRING, "杭州")), object("empty"));

        var result = transformer.transform(List.of(config, constant("id", ParamType.STRING, "root")), Map.of());

        assertEquals(List.of("patient", "id"), new ArrayList<>(result.keySet()));
        var patient = (Map<?, ?>) result.get("patient");
        assertEquals(List.of("id", "address", "empty"), new ArrayList<>(patient.keySet()));
        assertEquals(List.of("id", "city"), new ArrayList<>(((Map<?, ?>) patient.get("address")).keySet()));
        assertEquals(Map.of("id", "P1", "address", Map.of("id", "A1", "city", "杭州"), "empty", Map.of()), patient);
    }

    @Test
    void duplicateNestedFieldsReportTheirObjectPath() {
        var config = object("patient", object("address",
                constant("id", ParamType.STRING, "A1"), constant("id", ParamType.STRING, "A2")));

        var failure = assertThrows(IllegalArgumentException.class, () -> map(config, Map.of()));

        assertEquals("MAPPING $.patient.address.id: 目标字段重复", failure.getMessage());
    }

    @ParameterizedTest
    @MethodSource("invalidNodes")
    void rejectsIncompleteAndIncompatibleNodes(ParamMappingConfig config, String diagnostic) {
        var failure = assertThrows(IllegalArgumentException.class, () -> map(config, Map.of()));

        assertTrue(failure.getMessage().contains(diagnostic), failure.getMessage());
    }

    private static Stream<Arguments> invalidNodes() {
        var leafWithChildren = name("value", ParamType.STRING, "$");
        leafWithChildren.setChildParams(List.of(constant("child", ParamType.STRING, "x")));
        var leafWithItems = constant("value", ParamType.STRING, "x");
        leafWithItems.setItems(List.of(constant(null, ParamType.STRING, "x")));
        var leafWithAlias = expression("value", ParamType.STRING, "'x'");
        leafWithAlias.setItemAlias("item");
        var objectWithTemplate = object("value");
        objectWithTemplate.setItemTemplate(constant(null, ParamType.STRING, "x"));
        var fixedWithChildren = fixed("value");
        fixedWithChildren.setChildParams(List.of(constant("child", ParamType.STRING, "x")));
        var wrongLoopType = loop("value", "$", "item", constant(null, ParamType.STRING, "x"));
        wrongLoopType.setTargetParamType(ParamType.STRING);
        var wrongFixedType = fixed("value");
        wrongFixedType.setTargetParamType(ParamType.INTEGER);
        return Stream.of(
                Arguments.of(constant(null, ParamType.STRING, "x"), "targetParamKey"),
                Arguments.of(constant(" ", ParamType.STRING, "x"), "targetParamKey"),
                Arguments.of(object("parent", constant("", ParamType.STRING, "x")), "$.parent.childParams[0]"),
                Arguments.of(ParamMappingConfig.builder().targetParamKey("value").targetParamType(ParamType.STRING).build(),
                        "必须指定 mappingType"),
                Arguments.of(constant("value", null, "x"), "有效的 targetParamType"),
                Arguments.of(leafWithChildren, "不适用于当前 mappingType"),
                Arguments.of(leafWithItems, "不适用于当前 mappingType"),
                Arguments.of(leafWithAlias, "不适用于当前 mappingType"),
                Arguments.of(objectWithTemplate, "不适用于当前 mappingType"),
                Arguments.of(fixedWithChildren, "不适用于当前 mappingType"),
                Arguments.of(wrongLoopType, "targetParamType 必须为 ARRAY"),
                Arguments.of(wrongFixedType, "targetParamType 必须为 ARRAY"),
                Arguments.of(fixed("value", constant("named", ParamType.STRING, "x")), "$.value[0]"),
                Arguments.of(fixed("value", (ParamMappingConfig) null), "$.value[0]: 元素模板不能为空"));
    }

    @Test
    void nullObjectFieldsAreRejectedWithTheirConfigurationPath() {
        var config = object("parent");
        config.setChildParams(Arrays.asList((ParamMappingConfig) null));

        var failure = assertThrows(IllegalArgumentException.class, () -> map(config, Map.of()));

        assertTrue(failure.getMessage().contains("$.parent.childParams[0]"), failure.getMessage());
    }

    @Test
    void siblingLoopsReuseAliasesAndRenderArrayElementsWithoutFlattening() {
        var config = List.of(
                loop("first", "$.groups", "item", loop(null, "item", "value",
                        name(null, ParamType.INTEGER, "value"))),
                loop("second", "$.codes", "item", name(null, ParamType.STRING, "item")));

        assertEquals(Map.of("first", List.of(List.of(1, 2), List.of(), List.of(3)), "second", List.of("A", "B")),
                transformer.transform(config, Map.of("groups", List.of(List.of(1, 2), List.of(), List.of(3)),
                        "codes", List.of("A", "B"))));
    }

    @Test
    void loopAliasesCannotBeReadFromSiblingFields() {
        var config = List.of(loop("rows", "$", "item", name(null, ParamType.STRING, "item")),
                name("outside", ParamType.STRING, "item"));

        var failure = assertThrows(IllegalArgumentException.class,
                () -> transformer.transform(config, List.of("A")));

        assertTrue(failure.getMessage().contains("$.outside: 未知上下文变量: item"), failure.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "1item"})
    void missingAndInvalidLoopAliasesAreRejectedBeforeReadingSource(String alias) {
        var failure = assertThrows(IllegalArgumentException.class, () -> map(
                loop("rows", "$", alias, constant(null, ParamType.STRING, "x")), List.of()));

        assertTrue(failure.getMessage().contains("$.rows: 无效或保留的循环变量名"), failure.getMessage());
    }

    @ParameterizedTest
    @MethodSource("nonArraySources")
    void nonArrayLoopSourcesReportTargetPath(Object source) {
        var failure = assertThrows(IllegalArgumentException.class, () -> map(
                loop("rows", "$.items", "item", name(null, ParamType.STRING, "item")), Map.of("items", source)));

        assertEquals("MAPPING $.rows: 循环源必须为数组: $.items", failure.getMessage());
    }

    private static Stream<Object> nonArraySources() {
        return Stream.of("[]", 1, false, Map.of("code", "A"));
    }

    @Test
    void fixedAndLoopArraysNestWithoutChangingElementCountOrOrder() {
        var config = fixed("groups", loop(null, "$.items", "item", fixed(null,
                        name(null, ParamType.STRING, "item.code"), constant(null, ParamType.INTEGER, 7))),
                object(null), constant(null, ParamType.STRING, null), fixed(null));

        assertEquals(Arrays.asList(List.of(List.of("A", 7), List.of("B", 7)), Map.of(), null, List.of()),
                map(config, Map.of("items", List.of(Map.of("code", "A"), Map.of("code", "B")))));
    }

    @ParameterizedTest
    @EnumSource(ParamType.class)
    void nullAndMissingExpressionResultsKeepFieldsForAllTypes(ParamType type) {
        for (String script : List.of("null", "$.missing")) {
            var result = transformer.transform(List.of(expression("value", type, script)), Map.of());

            assertTrue(result.containsKey("value"));
            assertNull(result.get("value"), script + " -> " + type);
        }
    }

    @Test
    void expressionResultsKeepNestedNullsAndJsonTypes() {
        var result = map(expression("value", ParamType.OBJECT,
                "({nested: {enabled: true, count: 2, ratio: 1.5, text: '中文', absent: null}, "
                        + "items: [null, {code: 'A'}, [1, false]]})"), Map.of());
        var nested = new LinkedHashMap<String, Object>();
        nested.put("enabled", true);
        nested.put("count", 2);
        nested.put("ratio", 1.5d);
        nested.put("text", "中文");
        nested.put("absent", null);

        assertEquals(Map.of("nested", nested, "items", Arrays.asList(null, Map.of("code", "A"), List.of(1, false))), result);
    }

    @ParameterizedTest
    @MethodSource("invalidExpressionRules")
    void expressionRulesMustBeNonBlankStrings(Object rule) {
        var config = expression("value", ParamType.STRING, null);
        config.setMappingRule(rule);

        var failure = assertThrows(IllegalArgumentException.class, () -> map(config, Map.of()));

        assertEquals("MAPPING $.value: EXPRESSION 的 mappingRule 必须为非空 JS 字符串", failure.getMessage());
    }

    private static Stream<Object> invalidExpressionRules() {
        return Stream.of(null, "", " \n\t", 1, true, List.of("'x'"), Map.of("script", "'x'"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nestedExpressionMutationsAndReturnedObjectsAreIsolatedAcrossFieldsAndCalls() {
        var profile = new LinkedHashMap<String, Object>();
        profile.put("tags", new ArrayList<>(List.of("root")));
        var meta = new LinkedHashMap<String, Object>();
        meta.put("tags", new ArrayList<>(List.of("item")));
        var payload = Map.of("profile", profile, "items", List.of(Map.of("meta", meta)));
        var config = List.of(
                expression("changed", ParamType.OBJECT, "$.profile.tags[0] = 'changed'; $.profile"),
                expression("original", ParamType.OBJECT, "$.profile"),
                loop("rows", "$.items", "item", object(null,
                        expression("changed", ParamType.OBJECT, "item.meta.tags[0] = 'changed'; item.meta"),
                        expression("original", ParamType.OBJECT, "item.meta"),
                        name("tag", ParamType.STRING, "item.meta.tags[0]"))),
                expression("outside", ParamType.STRING, "typeof item"));
        var expected = Map.of("changed", Map.of("tags", List.of("changed")),
                "original", Map.of("tags", List.of("root")), "outside", "undefined",
                "rows", List.of(Map.of("changed", Map.of("tags", List.of("changed")),
                        "original", Map.of("tags", List.of("item")), "tag", "item")));

        var first = transformer.transform(config, payload);
        assertEquals(expected, first);
        var outputTags = (List<String>) ((Map<?, ?>) first.get("original")).get("tags");
        assertNotSame(profile.get("tags"), outputTags);
        outputTags.add("output-only");

        assertEquals(Map.of("tags", List.of("root")), profile);
        assertEquals(Map.of("tags", List.of("item")), meta);
        assertEquals(expected, transformer.transform(config, payload));
        assertEquals(Map.of("original", Map.of("tags", List.of("next")), "outside", "undefined"),
                transformer.transform(List.of(expression("original", ParamType.OBJECT, "$.profile"),
                        expression("outside", ParamType.STRING, "typeof item")),
                        Map.of("profile", Map.of("tags", List.of("next")))));
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
                .mappingType(MappingType.ARRAY_FIXED).items(Arrays.asList(items)).build();
    }
}
