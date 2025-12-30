package com.helianhealth.agent.common.utils;

import com.alibaba.fastjson2.JSON;
import com.helianhealth.agent.common.base.XmlConstants;
import com.helianhealth.agent.common.factory.XmlParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class XmlUtils {

    private static final Logger log = LoggerFactory.getLogger(XmlUtils.class);

    /**
     * 将XML字符串转换为Map<String, Object>
     *
     * @param xml XML字符串
     * @return 转换后的Map对象
     */
    public static Map<String, Object> xmlToMap(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));

            return elementToMap(document.getDocumentElement());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse XML to Map", e);
        }
    }


    public static Map<String, Object> parseResponseXml(String responseXml, Map<String, Object> metaMap) {
        return parseXmlForConfig(responseXml, metaMap);
    }

    /**
     * 解析SOAP响应XML，支持命名空间配置
     *
     * @param xml 原始SOAP响应XML
     * @param metaMap    元信息
     * @return 解析后的Map对象
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseXmlForConfig(String xml, Map<String, Object> metaMap) {
        XmlParserFactory parserFactory = XmlParserFactory.getInstance();
        DocumentBuilder builder = parserFactory.getDocumentBuilder();
        // 转义字符全量处理配置
        boolean useEscape = Boolean.parseBoolean((String) metaMap.get(XmlConstants.USE_ESCAPE_KEY));
        if (useEscape) {
            xml = unescapeXml(xml);
        }

        try {
            // 获取xml中业务数据元素
            InputSource is = new InputSource(new StringReader(cleanXmlString(xml)));
            Document doc = builder.parse(is);
            Element bizDataElement;

            String elementName = (String) metaMap.get(XmlConstants.ELEMENT_NAME_KEY);
            String namespace = (String) metaMap.get(XmlConstants.NAMESPACE_KEY);
            if (StringUtils.hasText(elementName)) {
                // 1. 优先根据标签名获取（确保标签名唯一）
                NodeList bizDataNodes = doc.getElementsByTagName(elementName);
                if (bizDataNodes.getLength() == 1) {
                    boolean isCDATA = metaMap.get(XmlConstants.IS_CDATA_KEY) == null ||
                            Boolean.parseBoolean((String) metaMap.get(XmlConstants.IS_CDATA_KEY));
                    if (isCDATA) {
                        String dataContent = bizDataNodes.item(0).getTextContent();
                        String dataType = (String) metaMap.get(XmlConstants.DATA_TYPE_KEY);
                        if (dataType != null && dataType.equalsIgnoreCase(XmlConstants.JSON_KEY)) {
                            return JsonUtils.toMap(dataContent);
                        }
                        bizDataElement = builder.parse(new InputSource(new StringReader(dataContent))).getDocumentElement();
                    } else {
                        bizDataElement = (Element) bizDataNodes.item(0).getChildNodes().item(0);
                    }
                } else {
                    log.error("标签名[{}]对应的节点数量不为1，无法确定唯一业务数据元素", elementName);
                    throw new RuntimeException("无法确定唯一的业务数据元素，请检查标签名是否正确");
                }
            } else if (StringUtils.hasText(namespace)) {
                // 2. 使用命名空间查找
                String namespaceElementName = (String) metaMap.get(XmlConstants.NAMESPACE_ELEMENT_NAME_KEY);
                String namespaceDataElementName = (String) metaMap.get(XmlConstants.NAMESPACE_DATA_ELEMENT_NAME_KEY);

                if (!StringUtils.hasText(namespaceElementName) || !StringUtils.hasText(namespaceDataElementName)) {
                    log.error("命名空间配置不完整，缺少元素名称");
                    throw new RuntimeException("命名空间配置不完整");
                }

                NodeList namespaceNodes = doc.getElementsByTagNameNS(namespace, namespaceElementName);
                if (namespaceNodes.getLength() == 0) {
                    log.error("命名空间[{}]下未找到元素[{}]", namespace, namespaceElementName);
                    throw new RuntimeException("未找到命名空间对应的元素");
                }

                Element namespaceElement = (Element) namespaceNodes.item(0);
                Element namespaceDataElement = getElementByPath(namespaceElement, namespaceDataElementName);
                boolean isCDATA = metaMap.get(XmlConstants.IS_CDATA_KEY) == null ||
                        Boolean.parseBoolean((String) metaMap.get(XmlConstants.IS_CDATA_KEY));

                if (isCDATA) {
                    String dataContent = namespaceDataElement.getTextContent();
                    String dataType = (String) metaMap.get(XmlConstants.DATA_TYPE_KEY);
                    if (dataType != null && dataType.equalsIgnoreCase(XmlConstants.JSON_KEY)) {
                        return JsonUtils.toMap(dataContent);
                    }
                    bizDataElement = builder.parse(new InputSource(new StringReader(dataContent))).getDocumentElement();
                } else {
                    bizDataElement = namespaceDataElement;
                }
            } else {
                // 3. 整个XML即为业务数据
                bizDataElement = doc.getDocumentElement();
            }

            return (Map<String, Object>) parseElement(metaMap, bizDataElement);
        } catch (Exception e) {
            log.error("解析SOAP响应失败:", e);
            throw new RuntimeException("解析SOAP响应失败: " + e.getMessage(), e);
        } finally {
            parserFactory.clearThreadLocal();
        }
    }

    /**
     * 还原XML转义字符为原始字符
     * @param content 包含转义字符的文本内容
     * @return 处理后的原始内容
     */
    private static String unescapeXml(String content) {
        if (StringUtils.isEmpty(content)) {
            return content;
        }
        // &amp;必须放在最前面，避免破坏其他转义符结构
        return content.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    /**
     * 清洗XML字符串：移除换行、制表符、标签间的多余空格
     * 保留标签内的有效文本（如CDATA、业务值）
     */
    private static String cleanXmlString(String xml) {
        if (xml == null || xml.isEmpty()) {
            return xml;
        }
        // 1. 移除所有换行符(\n)、回车符(\r)、制表符(\t)
        xml = xml.replaceAll("\\r|\\n|\\t", "");
        // 2. 移除标签之间的多余空格（保留标签内的空格）
        // 正则说明：匹配 "><" 之间的任意空格，替换为 "><"
        xml = xml.replaceAll(">\\s+<", "><");
        return xml;
    }

    /**
     * 递归将Element转换为Map
     *
     * @param element XML元素
     * @return 转换后的Map对象
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> elementToMap(Element element) {
        Map<String, Object> resultMap = new LinkedHashMap<>();

        // 处理属性
        NamedNodeMap attributes = element.getAttributes();
        if (attributes != null && attributes.getLength() > 0) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                resultMap.put(attribute.getNodeName(), attribute.getNodeValue());
            }
        }

        // 收集文本内容或子元素
        StringBuilder textContent = new StringBuilder();
        List<Element> childElements = new ArrayList<>();

        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                childElements.add((Element) node);
            } else if (node.getNodeType() == Node.TEXT_NODE) {
                // 处理文本节点，过滤空白
                String text = node.getNodeValue().trim();
                if (!text.isEmpty()) {
                    textContent.append(text);
                }
            }
        }

        // 处理子元素
        for (Element childElement : childElements) {
            String tagName = childElement.getNodeName();
            Object childValue = elementToMap(childElement);

            // 如果子元素是纯文本节点，直接提取值
            if (childValue instanceof Map && ((Map<?, ?>) childValue).size() == 1) {
                Map<?, ?> childMap = (Map<?, ?>) childValue;
                Object firstValue = childMap.values().iterator().next();
                // 如果子节点只包含自身标签和文本值，直接使用文本值
                if (firstValue instanceof String && childMap.containsKey(tagName)) {
                    childValue = firstValue;
                }
            }

            // 处理列表情况
            if (resultMap.containsKey(tagName)) {
                Object existingValue = resultMap.get(tagName);
                if (existingValue instanceof List) {
                    ((List<Object>) existingValue).add(childValue);
                } else {
                    List<Object> list = new ArrayList<>();
                    list.add(existingValue);
                    list.add(childValue);
                    resultMap.put(tagName, list);
                }
            } else {
                resultMap.put(tagName, childValue);
            }
        }

        // 处理当前节点的文本内容（如果没有子元素但有文本）
        if (childElements.isEmpty() && textContent.length() > 0) {
            return createSingleValueMap(element.getNodeName(), textContent.toString());
        }

        // 如果是根节点或包含子元素的节点，返回完整Map
        return resultMap;
    }

    /**
     * 创建只包含一个键值对的Map
     */
    private static Map<String, Object> createSingleValueMap(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    public static Map<String, Object> parseRequestXml(String requestXml, Map<String, Object> metaMap) {
        return parseXmlForConfig(requestXml, metaMap);
    }

    /**
     * 根据点号分隔的路径递归查找命名空间元素下的子元素
     *
     * @param parentElement 父元素
     * @param path 以点号分隔的元素路径，如 "level1.level2.level3"
     * @return 找到的元素，如果找不到返回null
     */
    private static Element getElementByPath(Element parentElement, String path) {
        if (parentElement == null || path == null || path.isEmpty()) {
            return null;
        }

        String[] elements = path.split("\\.");
        Element currentElement = parentElement;

        for (String tagName : elements) {
            NodeList nodeList = currentElement.getElementsByTagName(tagName);

            if (nodeList.getLength() > 0) {
                currentElement = (Element) nodeList.item(0);
            } else {
                // 如果在路径中任何一点找不到元素，则返回null
                return null;
            }
        }

        return currentElement;
    }

        /**
     * 将Map转换为XML字符串，支持自定义根元素名称
     *
     * @param scheduleRsp 需要转换的Map数据
     * @param metaInfo    元信息（包含命名空间等配置）
     * @return 生成的XML字符串
     */
    public static String buildResponseXml(Map<String, Object> scheduleRsp, String metaInfo) {
        try {
            if (scheduleRsp == null || scheduleRsp.isEmpty()) {
                throw new IllegalArgumentException("scheduleRsp cannot be null or empty");
            }

            // 解析metaInfo获取配置
            Map<String, Object> metaMap = new HashMap<>();
            if (metaInfo != null && !metaInfo.isEmpty()) {
                metaMap = JSON.parseObject(metaInfo, Map.class);
            }

            // 获取配置信息
            String namespaceURI = (String) metaMap.get("responseNamespace");
            String resultElementName = (String) metaMap.get("resultElementName");

            // 如果配置中没有指定根元素名称，则使用scheduleRsp的第一个key作为根元素名称
            if (resultElementName == null || resultElementName.isEmpty()) {
                resultElementName = scheduleRsp.keySet().iterator().next();
            }

            // 创建文档
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            // 创建根元素
            Element rootElement;
            if (namespaceURI != null && !namespaceURI.isEmpty()) {
                // 使用命名空间创建根元素
                rootElement = doc.createElementNS(namespaceURI, resultElementName);
            } else {
                // 不使用命名空间创建根元素
                rootElement = doc.createElement(resultElementName);
            }
            doc.appendChild(rootElement);

            // 获取根元素对应的数据并转换为XML元素
            Object rootData = scheduleRsp.get(resultElementName);
            if (rootData instanceof Map) {
                mapToElement((Map<String, Object>) rootData, rootElement, doc);
            } else {
                // 即使值为null，也创建标签
                String textContent = rootData == null ? "" : rootData.toString();
                rootElement.appendChild(doc.createTextNode(textContent));
            }

            // 创建XML输出
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();

            // 设置输出属性
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            // 关键：设置不输出XML声明
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            DOMSource source = new DOMSource(doc);
            transformer.transform(source, result);

            return writer.toString();
        } catch (Exception e) {
            log.error("构建SOAP响应XML失败", e);
            throw new RuntimeException("构建SOAP响应XML失败: " + e.getMessage(), e);
        }
    }

    /**
     * 递归将Map转换为XML元素，确保null值也会生成对应的标签
     *
     * @param map    要转换的Map
     * @param parent 父元素
     * @param doc    文档对象
     */
    private static void mapToElement(Map<String, Object> map, Element parent, Document doc) {
        if (map == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // 处理属性
            if (key.startsWith("#")) {
                String attrValue = value != null ? value.toString() : "";
                parent.setAttribute(key.substring(1), attrValue);
                continue;
            }

            // 创建子元素 - 即使值为null也会创建标签
            Element childElement = doc.createElement(key);

            if (value instanceof Map) {
                // 如果值是Map，则递归处理
                mapToElement((Map<String, Object>) value, childElement, doc);
            } else if (value instanceof List) {
                // 如果值是List，则遍历每个元素
                List<?> list = (List<?>) value;
                for (Object item : list) {
                    if (item instanceof Map) {
                        Element listItem = doc.createElement(key);
                        mapToElement((Map<String, Object>) item, listItem, doc);
                        parent.appendChild(listItem);
                    } else {
                        // 列表项处理
                        Element listItem = doc.createElement(key);
                        String textContent = item != null ? item.toString() : "";
                        listItem.appendChild(doc.createTextNode(textContent));
                        parent.appendChild(listItem);
                    }
                }
                // 列表已通过循环添加，不需要再添加当前childElement
                continue;
            } else {
                // 普通值处理，包括null值
                String textContent = value != null ? value.toString() : "";
                childElement.appendChild(doc.createTextNode(textContent));
            }

            // 添加子元素到父元素
            parent.appendChild(childElement);
        }
    }

    /**
     * 解析XML节点
     * 核心规则：
     * 1. 有属性：文本（含空值）用标签名存，空文本设为 null
     * 2. 无属性：直接用标签名存文本，空文本设为 null
     * 3. 同标签名子元素自动转为 List
     */
    private static Object parseElement(Map<String, Object> metaContext, Element element) {
        // 处理纯文本节点（无子元素、无属性）
        if (isPlainTextNode(element)) {
            return parseTextValue(element);
        }

        // 处理复杂节点
        Map<String, Object> resultMap = new LinkedHashMap<>();
        String tagName = element.getNodeName();

        // 1. 属性解析
        addAttributesToMap(element, resultMap);

        // 2. 标签内容解析
        String textValue = parseTextValue(element);
        if (textValue != null) {
            resultMap.put(tagName, textValue);
        }

        // 3. 子元素解析
        Map<String, List<Element>> childElements = groupChildElementsByTagName(element);
        addChildElementsToMap(metaContext, element, childElements, resultMap);

        // 4. 简化纯容器节点（仅含一个子元素且无属性）
        return simplifyContainerNode(element, resultMap, childElements);
    }

    /**
     * 判断是否为纯文本节点（无子元素、无属性）
     */
    private static boolean isPlainTextNode(Element element) {
        // 场景1：普通纯文本节点或CDATA节节点（含文本/CDATA内容）
        boolean hasTextOrCDataNode = false;
        if (element.getChildNodes().getLength() == 1) {
            Node childNode = element.getChildNodes().item(0);
            // 同时判断普通文本节点(3)和CDATA节节点(4)
            hasTextOrCDataNode = childNode.getNodeType() == Node.TEXT_NODE
                    || childNode.getNodeType() == Node.CDATA_SECTION_NODE;
        }
        // 场景2：自闭合标签（无子节点）
        boolean isEmptyTag = element.getChildNodes().getLength() == 0;

        return (hasTextOrCDataNode || isEmptyTag) && element.getAttributes().getLength() == 0;
    }

    /**
     * 解析文本节点的值（过滤空白）
     */
    private static String parseTextValue(Element element) {
        StringBuilder textBuilder = new StringBuilder();
        NodeList childNodes = element.getChildNodes();

        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                String text = node.getTextContent().trim();
                if (!text.isEmpty()) {
                    textBuilder.append(text);
                }
            }
        }

        // 若文本长度为0，说明是空标签，返回null
        return textBuilder.length() > 0 ? textBuilder.toString() : null;
    }

    /**
     * 将元素的属性添加到Map
     */
    private static void addAttributesToMap(Element element, Map<String, Object> map) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null || attributes.getLength() == 0) {
            return;
        }

        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            map.put(attr.getNodeName(), attr.getNodeValue());
        }
    }

    /**
     * 将子元素按标签名分组
     */
    private static Map<String, List<Element>> groupChildElementsByTagName(Element element) {
        Map<String, List<Element>> childElements = new HashMap<>();
        NodeList childNodes = element.getChildNodes();

        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                Element child = (Element) node;
                String childTagName = child.getNodeName();
                childElements.computeIfAbsent(childTagName, k -> new ArrayList<>()).add(child);
            }
        }

        return childElements;
    }

    /**
     * 解析子元素并添加到Map
     */

    private static void addChildElementsToMap(Map<String, Object> metaContext,
                                              Element element,
                                              Map<String, List<Element>> childElements,
                                              Map<String, Object> map) {
        String tagName = element.getNodeName();
        for (Map.Entry<String, List<Element>> entry : childElements.entrySet()) {
            String childTagName = entry.getKey();
            List<Element> elements = entry.getValue();
            Object value = parseChildElementValue(metaContext, elements, isArrayTag(metaContext, childElements, tagName));
            map.put(childTagName, value);
        }
    }

    /**
     * 数组的场景:同名子节点数量大于1且未明确配置为对象
     */
    @SuppressWarnings("unchecked")
    private static Boolean isArrayTag(Map<String, Object> metaContext,
                                      Map<String, List<Element>> childElements,
                                      String tagName) {
        List<String> objectTagName = (List<String>) metaContext.get(XmlConstants.OBJECT_TAG_KEY);
        if (objectTagName == null || objectTagName.isEmpty()) {
            return childElements.size() == 1;
        } else {
            return childElements.size() == 1 && !objectTagName.contains(tagName);
        }
    }

    /**
     * 解析子元素的值（数组或单个对象）
     */
    private static Object parseChildElementValue(Map<String, Object> metaContext, List<Element> elements, boolean arrayTag) {
        // 数组场景;同名节点数大于1必然就是数组 || 子元素大小为1且未明确配置为对象
        if (elements.size() > 1 || arrayTag) {
            List<Object> list = new ArrayList<>();
            for (Element e : elements) {
                list.add(parseElement(metaContext, e));
            }
            return list;
        }

        // 单个对象场景
        return parseElement(metaContext, elements.get(0));
    }

    /**
     * 简化纯容器节点（消除冗余嵌套）
     */
    private static Object simplifyContainerNode(Element element, Map<String, Object> map, Map<String, List<Element>> childElements) {
        // 条件：无属性 + 只有一种子元素 + 无额外文本
        if (element.getAttributes().getLength() == 0
                && childElements.size() == 1
                && !map.containsKey(element.getNodeName())) {
            return map.values().iterator().next();
        }
        return map;
    }
}
