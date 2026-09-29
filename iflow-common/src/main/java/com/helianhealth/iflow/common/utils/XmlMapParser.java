package com.helianhealth.iflow.common.utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
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
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 XML 文档与普通 Java Map/List 结构互相转换。
 *
 * <p>根元素名会保留；属性以 {@code @} 为前缀，混合文本使用 {@code #text}，
 * 重复同名子元素转换为 List。DOCTYPE、外部实体、外部 DTD 和 Schema 均被禁用。
 */
public final class XmlMapParser {

    static final String TEXT_KEY = "#text";
    static final String ATTRIBUTE_PREFIX = "@";
    private static final Pattern XML_ENCODING = Pattern.compile(
            "(?i)<\\?xml[^>]*\\bencoding\\s*=\\s*(['\"])([^'\"]+)\\1");

    private XmlMapParser() {}

    public static Map<String, Object> parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        return parseDocument(new InputSource(new StringReader(xml))).value();
    }

    /** HTTP charset 优先；未指定时由 XML 解析器识别 BOM / XML 声明。 */
    public static ParsedXml parseBytes(byte[] xml, String encoding) {
        InputSource source = new InputSource(new ByteArrayInputStream(xml));
        source.setEncoding(encoding);
        return parseDocument(source);
    }

    /**
     * 不构建 XML 文档，只根据 HTTP charset、BOM/字节签名和 XML 声明解码原文。
     */
    public static String decodeBytes(byte[] xml, String httpEncoding) {
        try {
            Charset charset = httpEncoding != null
                    ? Charset.forName(httpEncoding)
                    : detectCharset(xml);
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(xml))
                    .toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode XML: invalid character encoding");
        }
    }

    private static Charset detectCharset(byte[] value) {
        if (startsWith(value, 0x00, 0x00, 0xFE, 0xFF)) {
            return Charset.forName("UTF-32BE");
        }
        if (startsWith(value, 0xFF, 0xFE, 0x00, 0x00)) {
            return Charset.forName("UTF-32LE");
        }
        if (startsWith(value, 0xEF, 0xBB, 0xBF)) {
            return StandardCharsets.UTF_8;
        }
        if (startsWith(value, 0xFE, 0xFF) || startsWith(value, 0x00, 0x3C, 0x00, 0x3F)) {
            return StandardCharsets.UTF_16BE;
        }
        if (startsWith(value, 0xFF, 0xFE) || startsWith(value, 0x3C, 0x00, 0x3F, 0x00)) {
            return StandardCharsets.UTF_16LE;
        }
        if (startsWith(value, 0x00, 0x00, 0x00, 0x3C)) {
            return Charset.forName("UTF-32BE");
        }
        if (startsWith(value, 0x3C, 0x00, 0x00, 0x00)) {
            return Charset.forName("UTF-32LE");
        }
        int probeLength = Math.min(value.length, 512);
        String probe = new String(value, 0, probeLength, StandardCharsets.ISO_8859_1);
        Matcher matcher = XML_ENCODING.matcher(probe);
        return matcher.find() ? Charset.forName(matcher.group(2)) : StandardCharsets.UTF_8;
    }

    private static boolean startsWith(byte[] value, int... prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((value[i] & 0xff) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    public record ParsedXml(Map<String, Object> value, String encoding) {}

    private static ParsedXml parseDocument(InputSource source) {
        try {
            DocumentBuilder builder = newDocumentBuilder();
            Document document = builder.parse(source);
            document.getDocumentElement().normalize();
            Element root = document.getDocumentElement();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(root.getNodeName(), toValue(root));
            // getInputEncoding() can retain the initial UTF-8 probe even when the XML
            // declaration switches to e.g. GB18030. HTTP encoding takes precedence.
            String encoding = source.getEncoding() != null ? source.getEncoding()
                    : document.getXmlEncoding() != null ? document.getXmlEncoding() : document.getInputEncoding();
            return new ParsedXml(result, encoding);
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
