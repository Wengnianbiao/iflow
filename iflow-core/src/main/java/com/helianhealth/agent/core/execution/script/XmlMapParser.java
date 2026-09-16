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
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 XML 文档与普通 Java Map/List 结构互相转换。
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

    /**
     * 将 {@link #parse(String)} 的单根对象写回 XML。{@code value} 必须是恰好包含一个根元素键的 Map。
     */
    public static String stringify(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            throw new IllegalArgumentException("XML stringify value must be a single-root object");
        }
        if (map.size() != 1) {
            throw new IllegalArgumentException("XML stringify value must have a single root element");
        }
        Map.Entry<?, ?> root = map.entrySet().iterator().next();
        Object key = root.getKey();
        if (key == null || String.valueOf(key).isBlank()) {
            throw new IllegalArgumentException("rootName must be provided for XML.stringify");
        }
        return stringify(String.valueOf(key), root.getValue());
    }

    /**
     * 以 {@code rootName} 为根元素，将对象/数组/标量写成 XML。约定与 {@link #parse(String)} 相同。
     */
    public static String stringify(String rootName, Object value) {
        if (rootName == null || rootName.isBlank()) {
            throw new IllegalArgumentException("rootName must be provided for XML.stringify");
        }
        try {
            DocumentBuilder builder = newDocumentBuilder();
            Document document = builder.newDocument();
            Element root = document.createElement(rootName);
            document.appendChild(root);
            appendValue(document, root, value);

            Transformer transformer = newTransformer();
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize XML: invalid document");
        }
    }

    private static Transformer newTransformer() throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (IllegalArgumentException ignored) {
            // Some JDK TransformerFactory implementations do not support these attributes.
        }
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        return transformer;
    }

    private static void appendValue(Document document, Element parent, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object child = entry.getValue();
                if (key.startsWith(ATTRIBUTE_PREFIX)) {
                    parent.setAttribute(
                            key.substring(ATTRIBUTE_PREFIX.length()),
                            String.valueOf(child));
                } else if (TEXT_KEY.equals(key)) {
                    parent.appendChild(document.createTextNode(String.valueOf(child)));
                } else if (child instanceof List<?> list) {
                    for (Object item : list) {
                        Element element = document.createElement(key);
                        parent.appendChild(element);
                        appendValue(document, element, item);
                    }
                } else {
                    Element element = document.createElement(key);
                    parent.appendChild(element);
                    appendValue(document, element, child);
                }
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                Element element = document.createElement("item");
                parent.appendChild(element);
                appendValue(document, element, item);
            }
            return;
        }
        parent.appendChild(document.createTextNode(String.valueOf(value)));
    }
}
