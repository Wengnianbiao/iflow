package com.helianhealth.agent.core.execution.script;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 XML 文档解析为普通 Java Map/List 结构。
 *
 * <p>根元素名会保留；属性以 {@code @} 为前缀，混合文本使用 {@code #text}，
 * 重复同名子元素转换为 List。DOCTYPE、外部实体、外部 DTD 和 Schema 均被禁用。
 */
public final class XmlMapParser {

    static final String TEXT_KEY = "#text";
    static final String ATTRIBUTE_PREFIX = "@";

    private XmlMapParser() {}

    public static Map<String, Object> parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        try {
            DocumentBuilder builder = newDocumentBuilder();
            Document document =
                    builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            document.getDocumentElement().normalize();
            Element root = document.getDocumentElement();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(root.getNodeName(), toValue(root));
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse XML: invalid document");
        }
    }

    static DocumentBuilder newDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) {
                // Parsing warnings do not change the resulting map.
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        return builder;
    }

    private static Object toValue(Element element) {
        Map<String, Object> map = new LinkedHashMap<>();

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            map.put(ATTRIBUTE_PREFIX + attr.getNodeName(), attr.getNodeValue());
        }

        StringBuilder text = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                addChild(map, child.getNodeName(), toValue((Element) child));
            } else if (child.getNodeType() == Node.TEXT_NODE
                    || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                String value = child.getNodeValue();
                if (value != null) {
                    text.append(value);
                }
            }
        }

        String trimmedText = text.toString().trim();
        if (map.isEmpty()) {
            return trimmedText;
        }
        if (!trimmedText.isEmpty()) {
            map.put(TEXT_KEY, trimmedText);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static void addChild(Map<String, Object> map, String name, Object value) {
        Object existing = map.get(name);
        if (existing == null) {
            map.put(name, value);
        } else if (existing instanceof List) {
            ((List<Object>) existing).add(value);
        } else {
            List<Object> list = new ArrayList<>();
            list.add(existing);
            list.add(value);
            map.put(name, list);
        }
    }
}
