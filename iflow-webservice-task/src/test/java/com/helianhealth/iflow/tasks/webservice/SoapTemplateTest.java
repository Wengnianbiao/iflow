package com.helianhealth.iflow.tasks.webservice;

import com.helianhealth.iflow.common.utils.XmlMapParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SoapTemplateTest {

    @Test
    void bindsNestedFieldsQuotedKeysArraysAndScalarTypes() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("patient", Map.of("name", "张三", "display name", "A", "a.b", 42));
        parameters.put("items", List.of(Map.of("value", true), 3.5));
        parameters.put("empty", null);
        String template = "<r><name>$.patient.name</name><alias>$.patient['display name']</alias>"
                + "<number>$['patient'][\"a.b\"]</number><bool>$.items[0].value</bool>"
                + "<decimal>$.items[1]</decimal><empty>$.empty</empty></r>";

        assertEquals("<r><name>张三</name><alias>A</alias><number>42</number><bool>true</bool>"
                + "<decimal>3.5</decimal><empty></empty></r>", SoapTemplate.render(template, parameters));
    }

    @Test
    void quotedKeysSupportEscapedQuotesBackslashesAndUnicode() {
        assertEquals("<r>ok</r>", SoapTemplate.render("<r>$['a\\'b\\\\c\\u0020d']</r>",
                Map.of("a'b\\c d", "ok")));
    }

    @Test
    void escapesTextAndBothAttributeQuoteStylesWithoutChangingExistingEntities() {
        String value = "<&>\"'\n\r\t";
        String xml = SoapTemplate.render("<r first=\"$.value\" second='$.value'>&amp; $.value</r>",
                Map.of("value", value));
        assertEquals("<r first=\"&lt;&amp;&gt;&quot;&apos;&#10;&#13;&#9;\" "
                + "second='&lt;&amp;&gt;&quot;&apos;&#10;&#13;&#9;'>&amp; &lt;&amp;&gt;&quot;&apos;\n&#13;\t</r>", xml);
        Map<?, ?> root = (Map<?, ?>) XmlMapParser.parse(xml).get("r");
        assertEquals(value, root.get("@first"));
        assertEquals(value, root.get("@second"));
    }

    @Test
    void bindsQuotedPropertiesInAttributes() {
        assertEquals("<r name=\"张三\"/>", SoapTemplate.render("<r name=\"$.patient['display name']\"/>",
                Map.of("patient", Map.of("display name", "张三"))));
    }

    @Test
    void preservesCdataTextAndSplitsClosingDelimiter() {
        String xml = SoapTemplate.render("<r><![CDATA[prefix $.text suffix]]></r>",
                Map.of("text", "<&>]]>$.notReinterpreted${business.text}"));
        assertEquals("<r><![CDATA[prefix <&>]]]]><![CDATA[>$.notReinterpreted${business.text} suffix]]></r>", xml);
        assertEquals("prefix <&>]]>$.notReinterpreted${business.text} suffix", XmlMapParser.parse(xml).get("r"));
    }

    @Test
    void doesNotReinterpretBusinessTextAndSupportsLiteralPaths() {
        assertEquals("<r>$.missing ${literal.text} $['missing'] $.missing</r>",
                SoapTemplate.render("<r>$.text $$['missing'] $$.missing</r>",
                        Map.of("text", "$.missing ${literal.text}")));
    }

    @Test
    void doesNotBindCommentsProcessingInstructionsOrNamespaceDeclarations() {
        String template = "<?xml version=\"1.0\"?><?instruction $.missing?>"
                + "<r xmlns:soap=\"urn:$.missing\"><!-- $.missing --><soap:value>$.workflow</soap:value></r>";
        assertEquals(template.replace(">$.workflow<", ">local<"),
                SoapTemplate.render(template, Map.of("workflow", "local")));
    }

    @Test
    void tagNamesAreNotInterpolated() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SoapTemplate.render("<$.missing/>", Map.of()));
        assertTrue(error.getMessage().contains("Invalid SOAP XML"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"$.missing", "$.patient.missing", "$.items[2]", "$.patient.name.field",
            "$.empty.field", "$.patient[0]", "$.items.name", "$.patient", "$.items"})
    void rejectsMissingPathsInvalidTraversalAndContainerValues(String path) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("patient", Map.of("name", "张三"));
        parameters.put("items", List.of(1));
        parameters.put("empty", null);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SoapTemplate.render("<r>" + path + "</r>", parameters));
        assertTrue(error.getMessage().contains(path), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"$.items[*]", "$.items[-1]", "$.items[0,1]", "$.items[0:1]",
            "$.items[?(@.id)]", "$.patient..name", "$.patient.name()", "$.patient.name+1",
            "$.", "$['unclosed]", "${$.patient.name}"})
    void rejectsUnsupportedPathSyntax(String path) {
        assertThrows(IllegalArgumentException.class,
                () -> SoapTemplate.render("<r>" + path + "</r>", Map.of("patient", Map.of("name", "A"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<r>", "<r><!-- comment</r>", "<r><![CDATA[text</r>", "<r attr='x></r>",
            "<r>&unknown;</r>", "<r/><second/>", "<soap:Envelope/>",
            "<!DOCTYPE r SYSTEM 'file:///missing'><r/>",
            "<!DOCTYPE r [<!ENTITY test SYSTEM 'file:///missing'>]><r>&test;</r>"})
    void validatesFinalXml(String template) {
        assertThrows(IllegalArgumentException.class, () -> SoapTemplate.render(template, Map.of()));
    }

    @Test
    void rejectsInvalidXmlCharactersInParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> SoapTemplate.render("<r>$.text</r>", Map.of("text", "\u0000")));
    }

    @Test
    void xmlValidationReportsLocation() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SoapTemplate.render("<r>\n<child></r>", Map.of()));
        assertTrue(error.getMessage().contains("line 2"), error.getMessage());
    }
}
