package com.helianhealth.agent.core.execution.script;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
 * GraalJS 脚本内可用的 XML 解析/序列化桥。
 */
public class XmlBridge {

    private static final String TEXT_KEY = "#text";
    private static final String ATTRIBUTE_PREFIX = "@";

    public Object parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        try {
            DocumentBuilder builder = newSecureDocumentBuilderFactory().newDocumentBuilder();
            Document document =
                    builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            document.getDocumentElement().normalize();
            Element root = document.getDocumentElement();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(root.getNodeName(), toValue(root));
            return wrap(result);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse XML: " + e.getMessage(), e);
        }
    }

    public String stringify(String rootName, Object value) {
        if (rootName == null || rootName.isBlank()) {
            throw new IllegalArgumentException("rootName must be provided for XML.stringify");
        }
        try {
            DocumentBuilder builder = newSecureDocumentBuilderFactory().newDocumentBuilder();
            Document document = builder.newDocument();
            Element root = document.createElement(rootName);
            document.appendChild(root);
            appendValue(document, root, unwrap(value));

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize XML: " + e.getMessage(), e);
        }
    }

    private static DocumentBuilderFactory newSecureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private Object toValue(Element element) {
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
    private void addChild(Map<String, Object> map, String name, Object value) {
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

    @SuppressWarnings("unchecked")
    private Object wrap(Object value) {
        if (value instanceof Map) {
            Map<String, Object> wrapped = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                wrapped.put(entry.getKey(), wrap(entry.getValue()));
            }
            return ProxyObject.fromMap(wrapped);
        }
        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            Object[] wrapped = new Object[list.size()];
            for (int i = 0; i < list.size(); i++) {
                wrapped[i] = wrap(list.get(i));
            }
            return ProxyArray.fromArray(wrapped);
        }
        return value;
    }

    private Object unwrap(Object value) {
        if (value instanceof Value polyglotValue) {
            return unwrapPolyglot(polyglotValue);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), unwrap(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(unwrap(item));
            }
            return result;
        }
        return value;
    }

    private Object unwrapPolyglot(Value value) {
        if (value.isNull()) {
            return null;
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            if (value.fitsInLong()) {
                return value.asLong();
            }
            return value.asDouble();
        }
        if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>((int) value.getArraySize());
            for (long i = 0; i < value.getArraySize(); i++) {
                list.add(unwrap(value.getArrayElement((int) i)));
            }
            return list;
        }
        if (value.hasMembers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, unwrap(value.getMember(key)));
            }
            return map;
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendValue(Document document, Element parent, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                String key = entry.getKey();
                Object child = entry.getValue();
                if (key.startsWith(ATTRIBUTE_PREFIX)) {
                    parent.setAttribute(
                            key.substring(ATTRIBUTE_PREFIX.length()), String.valueOf(child));
                } else if (TEXT_KEY.equals(key)) {
                    parent.appendChild(document.createTextNode(String.valueOf(child)));
                } else if (child instanceof List) {
                    for (Object item : (List<Object>) child) {
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
        } else if (value instanceof List) {
            for (Object item : (List<Object>) value) {
                Element element = document.createElement("item");
                parent.appendChild(element);
                appendValue(document, element, item);
            }
        } else {
            parent.appendChild(document.createTextNode(String.valueOf(value)));
        }
    }
}
