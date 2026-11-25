package com.helianhealth.iflow.core.execution.script;

import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScriptEvaluatorBindingsTest {
    @AutoClose
    private final ScriptEvaluator evaluator = new ScriptEvaluator(List.of());

    @Test
    void bindingsAreAvailableAndDoNotLeakAfterSuccessOrFailure() {
        assertEquals("P1:A", evaluator.eval("$.id + ':' + item.code",
                Map.of("id", "P1"), () -> Map.of("item", Map.of("code", "A"))));
        assertEquals("undefined", evaluator.eval("typeof item", Map.of()));
        assertThrows(RuntimeException.class, () -> evaluator.eval("throw new Error('test')",
                Map.of(), () -> Map.of("item", "A")));
        assertEquals("undefined", evaluator.eval("typeof item", Map.of()));
        assertEquals("B", evaluator.eval("item", Map.of(), () -> Map.of("item", "B")));
    }

    @Test
    void reservedBindingsCannotReplaceExistingScriptGlobals() {
        assertThrows(IllegalArgumentException.class, () -> evaluator.eval("$", "root", () -> Map.of("$", "other")));
        assertThrows(IllegalArgumentException.class, () -> evaluator.eval("JSON", Map.of(), () -> Map.of("JSON", "other")));
        assertEquals("{}", evaluator.eval("JSON.stringify({})", Map.of()));
    }

    @Test
    void jsonInputsAndLoopVariablesExposeEnumerableMembersAndArrayMethods() {
        Map<String, Object> input = Map.of("items", List.of(Map.of("id", 1), Map.of("id", 2)));
        assertEquals("[{\"id\":1},{\"id\":2}]", evaluator.eval("JSON.stringify($.items)", input));
        assertEquals(List.of("id"), evaluator.eval("Object.keys($.items[0])", input));
        assertEquals(List.of(2, 3), evaluator.eval("$.items.map(item => item.id + 1)", input));
        assertEquals(true, evaluator.eval("Array.isArray($.items)", input));
        assertEquals("{\"id\":1}", evaluator.eval("JSON.stringify(item)", input, () -> Map.of("item", Map.of("id", 1))));
        assertEquals(input, evaluator.eval("$", input));
    }

    @Test
    void formerCryptoNameCanBeProvidedByTheCaller() {
        assertEquals("undefined", evaluator.eval("typeof Crypto", Map.of()));
        assertEquals("custom", evaluator.eval("Crypto", Map.of(), () -> Map.of("Crypto", "custom")));
    }

    @Test
    void discardsContextWhenTheScriptPreventsBindingRemoval() {
        evaluator.eval("Object.defineProperty(globalThis, 'item', { configurable: false }); void 0",
                Map.of(), () -> Map.of("item", "first"));
        assertEquals("second", evaluator.eval("item", Map.of(), () -> Map.of("item", "second")));
    }

    @Test
    void xmlStringifyUsesSharedMapConvention() {
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("code", "82601230009");
        patient.put("tag", List.of("urgent", "lab"));
        assertEquals(
                "<his><patient><code>82601230009</code><tag>urgent</tag><tag>lab</tag></patient></his>",
                evaluator.eval("XML.stringify('his', $.payload)", Map.of("payload", Map.of("patient", patient))));
    }

    @Test
    void filterKeepsBigDecimalFieldsAsNumbers() {
        Map<String, Object> lis = new LinkedHashMap<>();
        lis.put("applyId", "80158311");
        lis.put("serviceProviderType", "LIS");
        lis.put("price", new BigDecimal("1500"));
        lis.put("priceYuan", new BigDecimal("15.0"));
        Map<String, Object> other = new LinkedHashMap<>();
        other.put("applyId", "80158312");
        other.put("serviceProviderType", "OTHER");
        other.put("price", new BigDecimal("500"));

        Object result = evaluator.eval(
                "($.items || []).filter(item => String(item?.serviceProviderType ?? '') === 'LIS')",
                Map.of("items", List.of(lis, other)));

        List<?> items = assertInstanceOf(List.class, result);
        assertEquals(1, items.size());
        Map<?, ?> item = assertInstanceOf(Map.class, items.get(0));
        assertEquals("80158311", item.get("applyId"));
        assertEquals(1500, ((Number) item.get("price")).intValue());
        assertEquals(15, ((Number) item.get("priceYuan")).intValue());
        assertFalse(item.get("price") instanceof Map);
    }
}
