package com.helianhealth.iflow.tasks.webservice;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SOAP 的局部参数绑定。只扫描原始模板，插入的业务文本不参与表达式解析。
 * 不序列化整个 XML 文档，以保留模板中的命名空间、空白及 CDATA。
 */
final class SoapTemplate {

    private static final Pattern ATTRIBUTE =
            Pattern.compile("([^\\s=<>/]+)(\\s*=\\s*)([\"'])(.*?)\\3", Pattern.DOTALL);

    private SoapTemplate() {}

    static String render(String template, Map<String, Object> parameters) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("Missing soapTemplate");
        }
        Map<String, Object> root = parameters == null ? Map.of() : parameters;
        StringBuilder xml = new StringBuilder(template.length());
        int cursor = 0;
        while (cursor < template.length()) {
            if (template.startsWith("<!--", cursor)) {
                cursor = copyThrough(template, cursor, "-->", xml);
            } else if (template.startsWith("<?", cursor)) {
                cursor = copyThrough(template, cursor, "?>", xml);
            } else if (template.startsWith("<![CDATA[", cursor)) {
                int end = closing(template, cursor + 9, "]]>");
                xml.append("<![CDATA[")
                        .append(bind(template.substring(cursor + 9, end), root, Context.CDATA))
                        .append("]]>");
                cursor = end + 3;
            } else if (template.charAt(cursor) == '<') {
                int end = tagEnd(template, cursor);
                String tag = template.substring(cursor, end + 1);
                // XML 声明及文档类型声明没有局部参数；最终 XML 校验禁用 DTD。
                xml.append(tag.startsWith("<!") ? tag : bindAttributes(tag, root));
                cursor = end + 1;
            } else {
                int end = template.indexOf('<', cursor);
                if (end < 0) end = template.length();
                xml.append(bind(template.substring(cursor, end), root, Context.TEXT));
                cursor = end;
            }
        }
        String result = xml.toString();
        validateXml(result);
        return result;
    }

    private static void validateXml(String xml) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External XML resources are unsupported");
        });
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.DTD) {
                        throw new XMLStreamException("DOCTYPE is unsupported", reader.getLocation());
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            String location = e.getLocation() == null ? ""
                    : " at line " + e.getLocation().getLineNumber() + ", column " + e.getLocation().getColumnNumber();
            throw new IllegalArgumentException("Invalid SOAP XML" + location + ": " + e.getMessage(), e);
        }
    }

    private static String bindAttributes(String tag, Map<String, Object> root) {
        Matcher attributes = ATTRIBUTE.matcher(tag);
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        while (attributes.find()) {
            result.append(tag, cursor, attributes.start(4));
            String name = attributes.group(1);
            String value = attributes.group(4);
            result.append(name.equals("xmlns") || name.startsWith("xmlns:")
                    ? value : bind(value, root, Context.ATTRIBUTE));
            cursor = attributes.end(4);
        }
        return result.append(tag, cursor, tag.length()).toString();
    }

    private static String bind(String source, Map<String, Object> root, Context context) {
        StringBuilder result = new StringBuilder(source.length());
        for (int cursor = 0; cursor < source.length();) {
            boolean literal = source.startsWith("$$.", cursor) || source.startsWith("$$[", cursor);
            int start = literal ? cursor + 1 : cursor;
            if (source.startsWith("$.", start) || source.startsWith("$[", start)) {
                if (!literal && cursor >= 2 && source.startsWith("${", cursor - 2)) {
                    throw pathError(source.substring(cursor), "use $.path instead of ${$.path}");
                }
                Path path = readPath(source, start);
                String expression = source.substring(start, path.end());
                if (literal) {
                    result.append(expression);
                } else {
                    Object value = resolve(root, path.parts(), expression);
                    if (value instanceof Map<?, ?> || value instanceof List<?>) {
                        throw pathError(expression, "objects and arrays require a concrete scalar field");
                    }
                    String text = value == null ? "" : value.toString();
                    result.append(context == Context.CDATA
                            ? text.replace("]]>", "]]]]><![CDATA[>")
                            : escape(text, context));
                }
                cursor = path.end();
            } else {
                result.append(source.charAt(cursor++));
            }
        }
        return result.toString();
    }

    private static Object resolve(Object root, List<Object> parts, String expression) {
        Object value = root;
        for (Object part : parts) {
            if (part instanceof String key) {
                if (!(value instanceof Map<?, ?> map)) {
                    throw pathError(expression, "cannot read a field from a non-object");
                }
                if (!map.containsKey(key)) throw pathError(expression, "missing field: " + key);
                value = map.get(key);
            } else {
                int index = (Integer) part;
                if (!(value instanceof List<?> list)) {
                    throw pathError(expression, "cannot index a non-array");
                }
                if (index >= list.size()) throw pathError(expression, "array index out of bounds: " + index);
                value = list.get(index);
            }
        }
        return value;
    }

    private static Path readPath(String source, int start) {
        List<Object> parts = new ArrayList<>();
        int cursor = start + 1;
        while (cursor < source.length()) {
            char character = source.charAt(cursor);
            if (character == '.') {
                int fieldStart = ++cursor;
                if (cursor == source.length() || !fieldStart(source.charAt(cursor))) {
                    throw invalidPath(source, start, cursor);
                }
                while (cursor < source.length() && fieldCharacter(source.charAt(cursor))) cursor++;
                parts.add(source.substring(fieldStart, cursor));
            } else if (character == '[') {
                cursor++;
                if (cursor == source.length()) throw invalidPath(source, start, cursor);
                char quote = source.charAt(cursor);
                if (quote == '\'' || quote == '"') {
                    StringBuilder key = new StringBuilder();
                    cursor++;
                    while (cursor < source.length() && source.charAt(cursor) != quote) {
                        char next = source.charAt(cursor++);
                        if (next == '\\') {
                            if (cursor == source.length()) throw invalidPath(source, start, cursor);
                            char escaped = source.charAt(cursor++);
                            switch (escaped) {
                                case '\\', '\'', '"', '/' -> key.append(escaped);
                                case 'b' -> key.append('\b');
                                case 'f' -> key.append('\f');
                                case 'n' -> key.append('\n');
                                case 'r' -> key.append('\r');
                                case 't' -> key.append('\t');
                                case 'u' -> {
                                    if (cursor + 4 > source.length()) throw invalidPath(source, start, cursor);
                                    try {
                                        key.append((char) Integer.parseInt(source.substring(cursor, cursor + 4), 16));
                                    } catch (NumberFormatException e) {
                                        throw invalidPath(source, start, cursor + 4);
                                    }
                                    cursor += 4;
                                }
                                default -> throw invalidPath(source, start, cursor);
                            }
                        } else {
                            key.append(next);
                        }
                    }
                    if (cursor == source.length()) throw invalidPath(source, start, cursor);
                    cursor++;
                    parts.add(key.toString());
                } else {
                    int indexStart = cursor;
                    while (cursor < source.length() && source.charAt(cursor) >= '0'
                            && source.charAt(cursor) <= '9') cursor++;
                    if (cursor == indexStart) throw invalidPath(source, start, cursor + 1);
                    try {
                        parts.add(Integer.parseInt(source.substring(indexStart, cursor)));
                    } catch (NumberFormatException e) {
                        throw invalidPath(source, start, cursor);
                    }
                }
                if (cursor == source.length() || source.charAt(cursor) != ']') {
                    throw invalidPath(source, start, cursor + 1);
                }
                cursor++;
            } else {
                break;
            }
        }
        // 拒绝函数及路径运算，避免把不支持的表达式当作部分路径发送。
        if (cursor < source.length() && "(*?+|=@".indexOf(source.charAt(cursor)) >= 0) {
            throw invalidPath(source, start, cursor + 1);
        }
        return new Path(parts, cursor);
    }

    private static boolean fieldStart(char value) {
        return Character.isLetter(value) || value == '_' || value == '$';
    }

    private static boolean fieldCharacter(char value) {
        return fieldStart(value) || Character.isDigit(value) || value == '-';
    }

    private static IllegalArgumentException invalidPath(String source, int start, int end) {
        return pathError(source.substring(start, Math.min(end, source.length())),
                "only fields, quoted property names and non-negative array indexes are supported");
    }

    private static IllegalArgumentException pathError(String path, String message) {
        return new IllegalArgumentException("SOAP template path " + path + ": " + message);
    }

    private static String escape(String value, Context context) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&' -> result.append("&amp;");
                case '<' -> result.append("&lt;");
                case '>' -> result.append("&gt;");
                case '"' -> result.append("&quot;");
                case '\'' -> result.append("&apos;");
                case '\n', '\r', '\t' -> {
                    if (context == Context.ATTRIBUTE) result.append("&#").append((int) character).append(';');
                    else if (character == '\r') result.append("&#13;");
                    else result.append(character);
                }
                default -> result.append(character);
            }
        }
        return result.toString();
    }

    private static int copyThrough(String source, int start, String delimiter, StringBuilder result) {
        int end = closing(source, start, delimiter) + delimiter.length();
        result.append(source, start, end);
        return end;
    }

    private static int closing(String source, int start, String delimiter) {
        int end = source.indexOf(delimiter, start);
        if (end < 0) throw new IllegalArgumentException("Invalid SOAP template: missing " + delimiter);
        return end;
    }

    private static int tagEnd(String source, int start) {
        char quote = 0;
        for (int cursor = start + 1; cursor < source.length(); cursor++) {
            char character = source.charAt(cursor);
            if (quote != 0) {
                if (character == quote) quote = 0;
            } else if (character == '\'' || character == '"') {
                quote = character;
            } else if (character == '>') {
                return cursor;
            }
        }
        throw new IllegalArgumentException("Invalid SOAP template: unclosed XML tag");
    }

    private enum Context { TEXT, ATTRIBUTE, CDATA }

    private record Path(List<Object> parts, int end) {}
}
