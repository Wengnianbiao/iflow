package com.helianhealth.agent.core.execution.function;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        Map<String, Object> output = executor.execute(FunctionType.JSON_PARSE, input);

        assertEquals("82601230009", output.get("patientCode"));
        assertEquals("B202601230001", output.get("barCode"));
        assertEquals("3", output.get("applyStatus"));
        assertEquals(Map.of("enabled", true), output.get("detail"));
        assertEquals(List.of("urgent", "lab"), output.get("tags"));
    }

    @Test
    void rejectsNonObjectJsonRoots() {
        assertEquals(
                "FUNCTION JSON_PARSE requires a JSON object root",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> executor.execute(FunctionType.JSON_PARSE, "[1, 2]"))
                        .getMessage());
        assertEquals(
                "FUNCTION JSON_PARSE requires a JSON object root",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> executor.execute(FunctionType.JSON_PARSE, "null"))
                        .getMessage());
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

        Map<String, Object> output = executor.execute(FunctionType.XML_PARSE, input);

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
