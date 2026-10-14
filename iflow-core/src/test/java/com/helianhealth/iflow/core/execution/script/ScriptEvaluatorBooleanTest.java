package com.helianhealth.iflow.core.execution.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ScriptEvaluatorBooleanTest {

    @ParameterizedTest
    @MethodSource("conditionResults")
    void convertsConditionResultsUsingBooleanAndPositiveNumberRules(String script, boolean expected) {
        assertEquals(expected, ScriptEvaluator.evalBool(script, Map.of()));
    }

    static Stream<Arguments> conditionResults() {
        // Boolean 保留原值，数字仅大于 0 时为真，其他类型均为假。
        return Stream.of(
                Arguments.of("true", true),
                Arguments.of("false", false),
                Arguments.of("1", true),
                Arguments.of("-1", false),
                Arguments.of("0.5", true),
                Arguments.of("0", false),
                Arguments.of("'true'", false),
                Arguments.of("'TRUE'", false),
                Arguments.of("'false'", false),
                Arguments.of("'other'", false),
                Arguments.of("''", false),
                Arguments.of("null", false),
                Arguments.of("undefined", false),
                Arguments.of("({})", false),
                Arguments.of("[]", false));
    }

    @Test
    void evaluatesExpressionsAndScriptFunctionsAgainstConditionInput() {
        Map<String, Object> input = Map.of("iteration", 1, "limit", 2, "body", Map.of("count", 3));
        assertTrue(ScriptEvaluator.evalBool("$.iteration < $.limit && $.body.count === 3", input));
        assertFalse(ScriptEvaluator.evalBool("(function () { return $.iteration >= $.limit; })()", input));
        assertTrue(ScriptEvaluator.evalBool("function scriptFun() { return $.iteration < $.limit; }; scriptFun();", input));
    }

    @Test
    void conditionScriptsPreserveOriginalInputAfterSuccessAndFailure() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("items", new ArrayList<>(List.of(1)));
        Map<String, Object> input = new LinkedHashMap<>(Map.of("nested", nested));

        assertTrue(ScriptEvaluator.evalBool("$.nested.items[0] = 2; $.extra = true; true", input));
        assertEquals(Map.of("nested", Map.of("items", List.of(1))), input);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ScriptEvaluator.evalBool("$.nested.items[0] = 3; throw new Error('condition failure')", input));
        assertTrue(failure.getMessage().contains("condition failure"));
        assertEquals(Map.of("nested", Map.of("items", List.of(1))), input);
        assertTrue(ScriptEvaluator.evalBool("$.nested.items.length === 1 && $.nested.items[0] === 1", input));
    }
}
