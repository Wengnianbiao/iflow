package com.helianhealth.iflow.common.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class XmlMapParserTest {
    @Test
    void decodedStringsAreNotReencodedAgainstTheirXmlDeclaration() {
        String xml = "<?xml version=\"1.0\" encoding=\"GB18030\"?><r>张三</r>";
        assertEquals(Map.of("r", "张三"), XmlMapParser.parse(xml));
        XmlMapParser.ParsedXml parsed = XmlMapParser.parseBytes(xml.getBytes(Charset.forName("GB18030")), null);
        assertEquals(XmlMapParser.parse(xml), parsed.value());
        assertEquals("GB18030", parsed.encoding());
    }

    @Test
    void retainsMappingAndCanSerializeParsedValues() {
        Map<String, Object> expected = Map.of("r", Map.of("@id", "01", "#text", "说明",
                "item", List.of("00123", "00456")));
        assertEquals(expected, XmlMapParser.parse("<r id=\"01\">说明<item>00123</item><item>00456</item></r>"));
        assertEquals(expected, XmlMapParser.parse(XmlMapParser.stringify(expected)));
        assertNull(XmlMapParser.parse(" "));
        assertThrows(IllegalArgumentException.class,
                () -> XmlMapParser.parse("<!DOCTYPE r [<!ENTITY x 'value'>]><r>&x;</r>"));
    }
}
