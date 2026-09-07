package com.helianhealth.agent.core.execution.script;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GraalJS 脚本内可用的 XML 解析/序列化桥。
 */
public class XmlBridge {

    public Object parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        return wrap(XmlMapParser.parse(xml));
    }

    public String stringify(String rootName, Object value) {
        if (rootName == null || rootName.isBlank()) {
            throw new IllegalArgumentException("rootName must be provided for XML.stringify");
        }
        try {
            DocumentBuilder builder = XmlMapParser.newDocumentBuilder();
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
                if (key.startsWith(XmlMapParser.ATTRIBUTE_PREFIX)) {
                    parent.setAttribute(
                            key.substring(XmlMapParser.ATTRIBUTE_PREFIX.length()),
                            String.valueOf(child));
                } else if (XmlMapParser.TEXT_KEY.equals(key)) {
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
