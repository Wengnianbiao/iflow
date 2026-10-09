package com.helianhealth.iflow.tasks.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.helianhealth.iflow.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.tasks.mapping.enums.MappingType;
import com.helianhealth.iflow.tasks.mapping.enums.ParamType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappingTaskTest {

    private MappingTask mappingTask;

    @BeforeEach
    void setup() {
        mappingTask = new MappingTask(new ParamMapTransformer(new GraalJSEvaluator()), new ObjectMapper());
    }

    @Test
    void mapsFieldsFromPayloadObject() {
        ParamMappingConfig mapping =
                ParamMappingConfig.builder()
                        .targetParamKey("patientCode")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("$.patientCode")
                        .build();

        Object result =
                executeMapping(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(mapping),
                                MappingTask.INPUT_PAYLOAD,
                                Map.of("patientCode", "2508190082")));

        assertInstanceOf(Map.class, result);
        assertEquals("2508190082", ((Map<?, ?>) result).get("patientCode"));
    }

    @Test
    void mapsExpressionFromPayloadObject() {
        ParamMappingConfig mapping =
                ParamMappingConfig.builder()
                        .targetParamKey("code")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.EXPRESSION)
                        .mappingRule("$.patientCode")
                        .build();

        Map<?, ?> result =
                executeMapping(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(mapping),
                                MappingTask.INPUT_PAYLOAD,
                                Map.of("patientCode", "P001")));

        assertEquals("P001", result.get("code"));
    }

    @Test
    void mapsNamedParametersStoredOnPayload() {
        ParamMappingConfig patientMapping =
                ParamMappingConfig.builder()
                        .targetParamKey("patientCode")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("$.patientCode")
                        .build();
        ParamMappingConfig hospitalMapping =
                ParamMappingConfig.builder()
                        .targetParamKey("hospitalCode")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("$.hospital.code")
                        .build();

        Map<?, ?> result =
                executeMapping(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(patientMapping, hospitalMapping),
                                MappingTask.INPUT_PAYLOAD,
                                Map.of(
                                        "patientCode",
                                        "P001",
                                        "hospital",
                                        Map.of("code", "H001"))));

        assertEquals(Map.of("patientCode", "P001", "hospitalCode", "H001"), result);
    }

    @Test
    void convertsMapParamsConfigToDomain() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("targetParamKey", "status");
        config.put("targetParamType", "STRING");
        config.put("mappingType", "CONSTANT");
        config.put("mappingRule", "OK");

        Map<?, ?> result =
                executeMapping(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(config),
                                MappingTask.INPUT_PAYLOAD,
                                Map.of()));

        assertEquals("OK", result.get("status"));
    }

    @Test
    void mapsConstantValue() {
        ParamMappingConfig mapping =
                ParamMappingConfig.builder()
                        .targetParamKey("status")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.CONSTANT)
                        .mappingRule("OK")
                        .build();

        Map<?, ?> result =
                executeMapping(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(mapping),
                                MappingTask.INPUT_PAYLOAD,
                                Map.of()));

        assertEquals("OK", result.get("status"));
    }

    @Test
    void mapsNestedObjectFromPayload() {
        ParamMappingConfig child =
                ParamMappingConfig.builder()
                        .targetParamKey("name")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("$.data.patientName")
                        .build();
        ParamMappingConfig root =
                ParamMappingConfig.builder()
                        .targetParamKey("patient")
                        .targetParamType(ParamType.OBJECT)
                        .mappingType(MappingType.OBJECT)
                        .childParams(List.of(child))
                        .build();

        Map<?, ?> result =
                executeMapping(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(root),
                                MappingTask.INPUT_PAYLOAD,
                                Map.of("data", Map.of("patientName", "张三"))));
        Map<?, ?> patient = (Map<?, ?>) result.get("patient");
        assertEquals("张三", patient.get("name"));
    }

    @Test
    void readsScalarPayloadDirectly() {
        ParamMappingConfig mapping =
                ParamMappingConfig.builder()
                        .targetParamKey("payload")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("$")
                        .build();

        Map<?, ?> result =
                executeMapping(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(mapping),
                                MappingTask.INPUT_PAYLOAD,
                                "plain-value"));

        assertEquals("plain-value", result.get("payload"));
    }

    @Test
    void explicitNullPayloadMapsMissingSourceToNull() {
        ParamMappingConfig mapping =
                ParamMappingConfig.builder()
                        .targetParamKey("value")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("$.value")
                        .build();
        // 声明缺少 payload 由 ParamMappingTaskMapper 校验；已声明的表达式可解析为 null。
        Map<String, Object> input = new LinkedHashMap<>();
        input.put(MappingTask.INPUT_PAYLOAD, null);
        input.put(MappingTask.INPUT_PARAMS_CONFIG, List.of(mapping));
        TaskModel task = execute(input);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertTrue(task.outputAsMap().containsKey("value"));
        assertNull(task.outputAsMap().get("value"));
    }

    @Test
    void writesMappedFieldsToOutputRoot() {
        ParamMappingConfig mapping =
                ParamMappingConfig.builder()
                        .targetParamKey("patientCode")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("$.patientCode")
                        .build();
        TaskModel task =
                execute(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(mapping),
                                MappingTask.INPUT_PAYLOAD,
                                Map.of("patientCode", "2508190082")));
        task.addOutput("stale", true);
        mappingTask.execute(newWorkflow(), task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(Map.of("patientCode", "2508190082"), task.getOutputData());
        assertFalse(task.outputAsMap().containsKey("result"));
        assertFalse(task.outputAsMap().containsKey("output"));
        assertFalse(task.outputAsMap().containsKey("stale"));
    }

    @ParameterizedTest
    @MethodSource("scalarConversions")
    void convertsScalarValuesToDeclaredTypes(ParamType type, Object source, Object expected) {
        ParamMappingConfig mapping = ParamMappingConfig.builder()
                .targetParamKey("value").targetParamType(type)
                .mappingType(MappingType.NAME).sourceParamKey("$.value").build();

        Map<?, ?> output = executeMapping(Map.of(
                MappingTask.INPUT_PAYLOAD, Map.of("value", source),
                MappingTask.INPUT_PARAMS_CONFIG, List.of(mapping)));

        assertEquals(expected, output.get("value"));
        assertEquals(expected.getClass(), output.get("value").getClass());
    }

    private static Stream<Arguments> scalarConversions() {
        return Stream.of(
                Arguments.of(ParamType.STRING, "  原值  ", "  原值  "),
                Arguments.of(ParamType.STRING, "", ""),
                Arguments.of(ParamType.STRING, 123, "123"),
                Arguments.of(ParamType.STRING, -1.25d, "-1.25"),
                Arguments.of(ParamType.STRING, false, "false"),
                Arguments.of(ParamType.INTEGER, 0, 0),
                Arguments.of(ParamType.INTEGER, -17, -17),
                Arguments.of(ParamType.INTEGER, 17L, 17),
                Arguments.of(ParamType.INTEGER, -1.75d, -1),
                Arguments.of(ParamType.INTEGER, " 17 ", 17),
                Arguments.of(ParamType.INTEGER, "-2147483648", Integer.MIN_VALUE),
                Arguments.of(ParamType.INTEGER, "2147483647", Integer.MAX_VALUE),
                Arguments.of(ParamType.LONG, 0L, 0L),
                Arguments.of(ParamType.LONG, -17L, -17L),
                Arguments.of(ParamType.LONG, 17, 17L),
                Arguments.of(ParamType.LONG, -1.75d, -1L),
                Arguments.of(ParamType.LONG, " -17 ", -17L),
                Arguments.of(ParamType.LONG, "2147483648", 2147483648L),
                Arguments.of(ParamType.LONG, "-9223372036854775808", Long.MIN_VALUE),
                Arguments.of(ParamType.LONG, "9223372036854775807", Long.MAX_VALUE),
                Arguments.of(ParamType.DOUBLE, 0d, 0d),
                Arguments.of(ParamType.DOUBLE, -1.25d, -1.25d),
                Arguments.of(ParamType.DOUBLE, -17, -17d),
                Arguments.of(ParamType.DOUBLE, 17L, 17d),
                Arguments.of(ParamType.DOUBLE, " 1.25 ", 1.25d),
                Arguments.of(ParamType.DOUBLE, "-1.25e2", -125d),
                Arguments.of(ParamType.BOOLEAN, true, true),
                Arguments.of(ParamType.BOOLEAN, false, false),
                Arguments.of(ParamType.BOOLEAN, 0, false),
                Arguments.of(ParamType.BOOLEAN, 1, true),
                Arguments.of(ParamType.BOOLEAN, -1, true),
                Arguments.of(ParamType.BOOLEAN, "true", true),
                Arguments.of(ParamType.BOOLEAN, "TRUE", true),
                Arguments.of(ParamType.BOOLEAN, "1", true),
                Arguments.of(ParamType.BOOLEAN, "false", false),
                Arguments.of(ParamType.BOOLEAN, "0", false),
                // Boolean strings intentionally keep the existing untrimmed, permissive conversion.
                Arguments.of(ParamType.BOOLEAN, " true ", false),
                Arguments.of(ParamType.BOOLEAN, " 1 ", false),
                Arguments.of(ParamType.BOOLEAN, "", false),
                Arguments.of(ParamType.BOOLEAN, " ", false),
                Arguments.of(ParamType.BOOLEAN, "other", false));
    }

    @ParameterizedTest
    @EnumSource(value = ParamType.class, names = {"STRING", "INTEGER", "LONG", "DOUBLE", "BOOLEAN"})
    void retainsExplicitNullFieldsForEveryScalarType(ParamType type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("value", null);
        var mapping = ParamMappingConfig.builder().targetParamKey("value").targetParamType(type)
                .mappingType(MappingType.NAME).sourceParamKey("$.value").build();

        Map<?, ?> output = executeMapping(Map.of("payload", payload, "paramsConfig", List.of(mapping)));

        assertTrue(output.containsKey("value"));
        assertNull(output.get("value"));
    }

    @ParameterizedTest
    @MethodSource("invalidScalarConversions")
    void invalidScalarConversionsFailWithTargetPath(ParamType type, Object source) {
        var mapping = ParamMappingConfig.builder().targetParamKey("value").targetParamType(type)
                .mappingType(MappingType.NAME).sourceParamKey("$.value").build();

        TaskModel task = execute(Map.of("payload", Map.of("value", source), "paramsConfig", List.of(mapping)));

        assertFailedWithoutOutput(task, "MAPPING $.value");
    }

    private static Stream<Arguments> invalidScalarConversions() {
        Stream<Arguments> invalidNumbers = Stream.of(ParamType.INTEGER, ParamType.LONG, ParamType.DOUBLE)
                .flatMap(type -> Stream.of("", "  ", "not-a-number").map(value -> Arguments.of(type, value)));
        Stream<Arguments> unsupportedValues = Stream.of(ParamType.INTEGER, ParamType.LONG, ParamType.DOUBLE, ParamType.BOOLEAN)
                .flatMap(type -> Stream.of(Map.of("value", 1), List.of(1)).map(value -> Arguments.of(type, value)));
        return Stream.concat(Stream.concat(invalidNumbers, unsupportedValues), Stream.of(
                Arguments.of(ParamType.INTEGER, "1.5"), Arguments.of(ParamType.LONG, "1.5"),
                Arguments.of(ParamType.INTEGER, "2147483648"), Arguments.of(ParamType.INTEGER, "-2147483649"),
                Arguments.of(ParamType.LONG, "9223372036854775808"), Arguments.of(ParamType.LONG, "-9223372036854775809"),
                Arguments.of(ParamType.INTEGER, true), Arguments.of(ParamType.LONG, true),
                Arguments.of(ParamType.DOUBLE, true)));
    }

    @Test
    void mapsArrayItemsUsingAliasAndRootPayload() {
        ParamMappingConfig code = ParamMappingConfig.builder()
                .targetParamKey("code").targetParamType(ParamType.STRING)
                .mappingType(MappingType.NAME).sourceParamKey("item.code").build();
        ParamMappingConfig patient = ParamMappingConfig.builder()
                .targetParamKey("patientId").targetParamType(ParamType.STRING)
                .mappingType(MappingType.NAME).sourceParamKey("$.patientId").build();
        ParamMappingConfig quantity = ParamMappingConfig.builder()
                .targetParamKey("quantity").targetParamType(ParamType.INTEGER)
                .mappingType(MappingType.EXPRESSION).mappingRule("item.qty * 2").build();
        ParamMappingConfig template = ParamMappingConfig.builder()
                .targetParamType(ParamType.OBJECT).mappingType(MappingType.OBJECT)
                .childParams(List.of(code, patient, quantity)).build();
        ParamMappingConfig array = ParamMappingConfig.builder()
                .targetParamKey("details").targetParamType(ParamType.ARRAY)
                .mappingType(MappingType.ARRAY_MAP).sourceParamKey("$.items")
                .itemAlias("item").itemTemplate(template).build();
        Map<String, Object> payload = Map.of("patientId", "P001", "items",
                List.of(Map.of("code", "A", "qty", 2), Map.of("code", "B", "qty", 3)));

        Map<?, ?> output = executeMapping(Map.of(
                MappingTask.INPUT_PAYLOAD, payload,
                MappingTask.INPUT_PARAMS_CONFIG, List.of(array)));

        assertEquals(List.of(
                Map.of("code", "A", "patientId", "P001", "quantity", 4),
                Map.of("code", "B", "patientId", "P001", "quantity", 6)), output.get("details"));
        assertEquals(2, ((Map<?, ?>) ((List<?>) payload.get("items")).get(0)).get("qty"));
    }

    @Test
    void expressionProducesNestedObjectsAndArraysWithoutOutputWrapper() {
        ParamMappingConfig object = ParamMappingConfig.builder()
                .targetParamKey("patient").targetParamType(ParamType.OBJECT)
                .mappingType(MappingType.EXPRESSION)
                .mappingRule("({id: $.patientId, enabled: true})").build();
        ParamMappingConfig array = ParamMappingConfig.builder()
                .targetParamKey("codes").targetParamType(ParamType.ARRAY)
                .mappingType(MappingType.EXPRESSION)
                .mappingRule("$.items.map(item => item.code)").build();

        Map<?, ?> output = executeMapping(Map.of(
                MappingTask.INPUT_PAYLOAD, Map.of("patientId", "P001", "items",
                        List.of(Map.of("code", "A"), Map.of("code", "B"))),
                MappingTask.INPUT_PARAMS_CONFIG, List.of(object, array)));

        assertEquals(Map.of("patient", Map.of("id", "P001", "enabled", true),
                "codes", List.of("A", "B")), output);
    }

    @ParameterizedTest
    @MethodSource("invalidConfigs")
    void invalidMappingConfigFailsTaskWithoutPublishingPartialBusinessOutput(
            Object config, String expectedDiagnostic) {
        TaskModel task = execute(Map.of(
                MappingTask.INPUT_PAYLOAD, Map.of("value", "not-a-number"),
                MappingTask.INPUT_PARAMS_CONFIG, config));

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains(expectedDiagnostic),
                task.getReasonForInCompletion());
        assertEquals(Map.of(), task.getOutputData());
    }

    private static Stream<Arguments> invalidConfigs() {
        Map<String, Object> valid = Map.of(
                "targetParamKey", "value", "targetParamType", "STRING",
                "mappingType", "CONSTANT", "mappingRule", "OK");
        Map<String, Object> unknownField = new LinkedHashMap<>(valid);
        unknownField.put("unexpected", true);
        return Stream.of(
                Arguments.of(List.of(unknownField), "unexpected"),
                Arguments.of(List.of(Map.of("targetParamKey", "value",
                        "targetParamType", "BAD_TYPE", "mappingType", "CONSTANT")), "BAD_TYPE"),
                Arguments.of(List.of(valid, valid), "MAPPING $.value: 目标字段重复"),
                Arguments.of(List.of(Map.of("targetParamType", "STRING",
                        "mappingType", "CONSTANT", "mappingRule", "OK")), "targetParamKey"),
                Arguments.of(List.of(Map.of("targetParamKey", "value",
                        "targetParamType", "STRING", "mappingType", "NAME",
                        "sourceParamKey", "unknown.value")), "未知上下文变量"),
                Arguments.of(List.of(Map.of("targetParamKey", "value",
                        "targetParamType", "INTEGER", "mappingType", "NAME",
                        "sourceParamKey", "$.value")), "MAPPING $.value"),
                Arguments.of(List.of(Map.of("targetParamKey", "value",
                        "targetParamType", "STRING", "mappingType", "EXPRESSION",
                        "mappingRule", "(() => { throw new Error('mapping rejected'); })()")),
                        "mapping rejected"));
    }

    @ParameterizedTest
    @MethodSource("nestedConfigFailures")
    void validatesJsonNodesRecursively(Object config, String diagnostic) {
        TaskModel task = execute(Map.of("payload", Map.of(), "paramsConfig", config));

        assertFailedWithoutOutput(task, diagnostic);
    }

    private static Stream<Arguments> nestedConfigFailures() {
        var leaf = Map.<String, Object>of("targetParamType", "STRING", "mappingType", "CONSTANT", "mappingRule", "OK");
        var invalidLeaves = List.of(
                Arguments.of(withProperty(leaf, "unexpected", true), "unexpected"),
                Arguments.of(withProperty(leaf, "sourceParamType", "STRING"), "sourceParamType"),
                Arguments.of(withProperty(leaf, "targetParamType", "INVALID_TYPE"), "INVALID_TYPE"),
                Arguments.of(withProperty(leaf, "mappingType", "INVALID_MODE"), "INVALID_MODE"),
                Arguments.of(withProperty(leaf, "childParams", List.of(withProperty(leaf, "targetParamKey", "child"))),
                        "不适用于当前 mappingType"),
                Arguments.of(withProperty(leaf, "items", List.of(leaf)), "不适用于当前 mappingType"),
                Arguments.of(withProperty(leaf, "itemAlias", "item"), "不适用于当前 mappingType"),
                Arguments.of(withProperty(leaf, "itemTemplate", leaf), "不适用于当前 mappingType"));
        return invalidLeaves.stream().flatMap(args -> {
            @SuppressWarnings("unchecked")
            var invalidLeaf = (Map<String, Object>) args.get()[0];
            String diagnostic = (String) args.get()[1];
            return Stream.of(
                    Arguments.of(List.of(withProperty(invalidLeaf, "targetParamKey", "value")), diagnostic),
                    Arguments.of(List.of(Map.of("targetParamKey", "parent", "targetParamType", "OBJECT",
                            "mappingType", "OBJECT", "childParams", List.of(withProperty(invalidLeaf, "targetParamKey", "value")))),
                            diagnostic),
                    Arguments.of(List.of(Map.of("targetParamKey", "rows", "targetParamType", "ARRAY",
                            "mappingType", "ARRAY_MAP", "sourceParamKey", "$.missing", "itemAlias", "item",
                            "itemTemplate", invalidLeaf)), diagnostic),
                    Arguments.of(List.of(Map.of("targetParamKey", "values", "targetParamType", "ARRAY",
                            "mappingType", "ARRAY_FIXED", "items", List.of(invalidLeaf))), diagnostic));
        });
    }

    @Test
    void parsesMixedNestedJsonTemplatesAndDefaultsEmptyContainers() {
        var config = List.of(
                Map.of("targetParamKey", "emptyObject", "targetParamType", "OBJECT", "mappingType", "OBJECT"),
                Map.of("targetParamKey", "emptyArray", "targetParamType", "ARRAY", "mappingType", "ARRAY_FIXED"),
                Map.of("targetParamKey", "rows", "targetParamType", "ARRAY", "mappingType", "ARRAY_MAP",
                        "sourceParamKey", "$.items", "itemAlias", "item", "itemTemplate", Map.of(
                                "targetParamType", "ARRAY", "mappingType", "ARRAY_FIXED", "items", List.of(
                                        Map.of("targetParamType", "OBJECT", "mappingType", "OBJECT", "childParams", List.of(
                                                Map.of("targetParamKey", "code", "targetParamType", "STRING",
                                                        "mappingType", "NAME", "sourceParamKey", "item.code"))),
                                        Map.of("targetParamType", "INTEGER", "mappingType", "CONSTANT", "mappingRule", 7)))));

        Map<?, ?> output = executeMapping(Map.of("payload", Map.of("items", List.of(Map.of("code", "A"))),
                "paramsConfig", config));

        assertEquals(Map.of("emptyObject", Map.of(), "emptyArray", List.of(),
                "rows", List.of(List.of(Map.of("code", "A"), 7))), output);
    }

    @ParameterizedTest
    @MethodSource("renderFailuresAfterSuccessfulFields")
    void failuresAfterSuccessfulFieldsDoNotPublishPartialOutput(
            Object failingNode, Object payload, String targetPath, String diagnostic) {
        var first = Map.of("targetParamKey", "completed", "targetParamType", "STRING",
                "mappingType", "CONSTANT", "mappingRule", "OK");

        TaskModel task = execute(Map.of("payload", payload, "paramsConfig", List.of(first, failingNode)));

        assertFailedWithoutOutput(task, "MAPPING " + targetPath);
        assertTrue(task.getReasonForInCompletion().contains(diagnostic), task.getReasonForInCompletion());
    }

    private static Stream<Arguments> renderFailuresAfterSuccessfulFields() {
        var quantity = Map.of("targetParamKey", "quantity", "targetParamType", "INTEGER",
                "mappingType", "NAME", "sourceParamKey", "$.quantity");
        var row = Map.of("targetParamType", "OBJECT", "mappingType", "OBJECT", "childParams", List.of(
                Map.of("targetParamKey", "quantity", "targetParamType", "INTEGER", "mappingType", "NAME",
                        "sourceParamKey", "item.qty")));
        var loop = Map.of("targetParamKey", "rows", "targetParamType", "ARRAY", "mappingType", "ARRAY_MAP",
                "sourceParamKey", "$.items", "itemAlias", "item", "itemTemplate", row);
        var fixed = Map.of("targetParamKey", "values", "targetParamType", "ARRAY", "mappingType", "ARRAY_FIXED",
                "items", List.of(Map.of("targetParamType", "INTEGER", "mappingType", "CONSTANT", "mappingRule", 1),
                        Map.of("targetParamType", "OBJECT", "mappingType", "OBJECT", "childParams", List.of(quantity))));
        return Stream.of(
                Arguments.of(quantity, Map.of("quantity", "bad"), "$.quantity", "bad"),
                Arguments.of(loop, Map.of("items", List.of(Map.of("qty", "1"), Map.of("qty", "bad"))),
                        "$.rows[1].quantity", "bad"),
                Arguments.of(loop, Map.of("items", Map.of()), "$.rows", "循环源必须为数组"),
                Arguments.of(fixed, Map.of("quantity", "bad"), "$.values[1].quantity", "bad"),
                Arguments.of(expressionNode("STRING", "const = ;"), Map.of(), "$.value", "evaluating script"),
                Arguments.of(expressionNode("STRING", "(() => { throw new Error('rejected'); })()"),
                        Map.of(), "$.value", "rejected"),
                Arguments.of(expressionNode("OBJECT", "'wrong'"), Map.of(), "$.value", "映射结果必须为对象"),
                Arguments.of(expressionNode("ARRAY", "({code: 'wrong'})"), Map.of(), "$.value", "映射结果必须为数组"));
    }

    @Test
    void validatesEntireTreeBeforeEvaluatingEarlierFields() {
        var first = expressionNode("STRING", "(() => { throw new Error('must not execute'); })()");
        var invalid = Map.of("targetParamKey", "parent", "targetParamType", "OBJECT", "mappingType", "OBJECT",
                "childParams", List.of(Map.of("targetParamKey", "child", "mappingType", "CONSTANT")));

        TaskModel task = execute(Map.of("payload", Map.of(), "paramsConfig", List.of(first, invalid)));

        assertFailedWithoutOutput(task, "MAPPING $.parent.child");
        assertFalse(task.getReasonForInCompletion().contains("must not execute"));
    }

    private static Map<String, Object> expressionNode(String type, String script) {
        return Map.of("targetParamKey", "value", "targetParamType", type, "mappingType", "EXPRESSION", "mappingRule", script);
    }

    private static Map<String, Object> withProperty(Map<String, Object> node, String key, Object value) {
        var result = new LinkedHashMap<>(node);
        result.put(key, value);
        return result;
    }

    @Test
    void strictConfigValidationDoesNotModifyProvidedObjectMapper() {
        ObjectMapper sharedMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        MappingTask strictTask = new MappingTask(
                new ParamMapTransformer(new GraalJSEvaluator()), sharedMapper);
        TaskModel task = new TaskModel();
        task.setInputData(Map.of(
                MappingTask.INPUT_PAYLOAD, Map.of(),
                MappingTask.INPUT_PARAMS_CONFIG, List.of(Map.of(
                        "targetParamKey", "status", "targetParamType", "STRING",
                        "mappingType", "CONSTANT", "mappingRule", "OK", "unknownField", true))));

        strictTask.execute(newWorkflow(), task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("unknownField"));
        assertFalse(sharedMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        assertEquals(Map.of(), task.getOutputData());
    }

    private Map<?, ?> executeMapping(Map<String, Object> input) {
        TaskModel task = execute(input);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        return (Map<?, ?>) task.getOutputData();
    }

    private TaskModel execute(Map<String, Object> input) {
        TaskModel task = new TaskModel();
        task.setTaskId("mapping-task");
        task.setInputData(input);
        mappingTask.execute(newWorkflow(), task, null);
        return task;
    }

    private static WorkflowModel newWorkflow() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        return workflow;
    }

    private static void assertFailedWithoutOutput(TaskModel task, String diagnostic) {
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains(diagnostic), task.getReasonForInCompletion());
        assertEquals(Map.of(), task.getOutputData());
    }
}
