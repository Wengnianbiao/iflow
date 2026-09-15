package com.helianhealth.agent.core.execution.function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FunctionExecutorTest {

    private final FunctionExecutor executor = new FunctionExecutor(1_000);

    @Test
    void parsesJsonObjectIntoPlainMap() {
        String input = """
                {
                  "patientCode": "82601230009",
                  "barCode": "B202601230001",
                  "applyStatus": "3",
                  "detail": {"enabled": true},
                  "tags": ["urgent", "lab"]
                }
                """;

        Map<?, ?> output = assertInstanceOf(Map.class, executor.execute(FunctionType.JSON_PARSE, input));

        assertEquals("82601230009", output.get("patientCode"));
        assertEquals("B202601230001", output.get("barCode"));
        assertEquals("3", output.get("applyStatus"));
        assertEquals(Map.of("enabled", true), output.get("detail"));
        assertEquals(List.of("urgent", "lab"), output.get("tags"));
    }

    @ParameterizedTest
    @MethodSource("jsonValues")
    void parsesAnyJsonRoot(String input, Object expected) {
        assertEquals(expected, executor.execute(FunctionType.JSON_PARSE, input));
    }

    private static Stream<Arguments> jsonValues() {
        return Stream.of(
                Arguments.of("{}", Map.of()),
                Arguments.of("[]", List.of()),
                Arguments.of("[1, {\"items\":[true, null]}]",
                        List.of(1, Map.of("items", Arrays.asList(true, null)))),
                Arguments.of("\"hello\"", "hello"),
                Arguments.of("\"\"", ""),
                Arguments.of("42", 42),
                Arguments.of("2147483648", 2147483648L),
                Arguments.of("1.25", new BigDecimal("1.25")),
                Arguments.of("true", true),
                Arguments.of("false", false),
                Arguments.of("null", null));
    }

    @Test
    void rejectsInvalidJsonWithoutEchoingInput() {
        String sensitiveInput = "{patientCode: 'secret'";

        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> executor.execute(FunctionType.JSON_PARSE, sensitiveInput));

        assertEquals("FUNCTION JSON_PARSE failed: invalid JSON", error.getMessage());
    }

    @Test
    void parsesXmlUsingStableMapConvention() {
        String input = """
                <patient active="true">
                  <code>82601230009</code>
                  <tag>urgent</tag>
                  <tag>lab</tag>
                  <note priority="high">review</note>
                </patient>
                """;

        Map<?, ?> output = assertInstanceOf(Map.class, executor.execute(FunctionType.XML_PARSE, input));

        assertEquals(
                Map.of(
                        "@active", "true",
                        "code", "82601230009",
                        "tag", List.of("urgent", "lab"),
                        "note", Map.of("@priority", "high", "#text", "review")),
                output.get("patient"));
    }

    @Test
    void rejectsXmlDoctype() {
        String input = """
                <!DOCTYPE patient [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <patient>&xxe;</patient>
                """;

        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> executor.execute(FunctionType.XML_PARSE, input));

        assertEquals("FUNCTION XML_PARSE failed: invalid XML", error.getMessage());
    }

    @Test
    void rejectsBlankAndOversizedInputs() {
        FunctionExecutor smallExecutor = new FunctionExecutor(5);

        assertEquals(
                "FUNCTION input 'value' must not be blank",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> smallExecutor.execute(FunctionType.JSON_PARSE, "  "))
                        .getMessage());
        assertEquals(
                "FUNCTION input exceeds iflow.function.max-input-chars=5",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> smallExecutor.execute(FunctionType.JSON_PARSE, "{\"a\":1}"))
                        .getMessage());
    }

    @Test
    void rejectsNonPositiveConfiguredLimit() {
        assertThrows(IllegalArgumentException.class, () -> new FunctionExecutor(0));
    }
}
