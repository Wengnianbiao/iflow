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

    public static Map<String, Object> parseResponseXml(String responseXml, Map<String, Object> metaMap) {
        return parseXmlForConfig(responseXml, metaMap);
    }

    /**
     * 解析XML，支持标签名定位业务数据
     *
     * @param xml     原始XML
     * @param metaMap 元信息
     * @return 解析后的Map对象
     */
    private static Map<String, Object> parseXmlForConfig(String xml, Map<String, Object> metaMap) {
        XmlParserFactory parserFactory = XmlParserFactory.getInstance();
        DocumentBuilder builder = parserFactory.getDocumentBuilder();

        // 转义字符全量处理配置
        Boolean useEscapeObj = (Boolean) metaMap.get(XmlConstants.USE_ESCAPE_KEY);
        boolean useEscape = useEscapeObj != null && useEscapeObj;
        if (useEscape) {
            xml = unescapeXml(xml);
        }

        try {
            InputSource is = new InputSource(new StringReader(cleanXmlString(xml)));
            Document doc = builder.parse(is);

            String elementName = (String) metaMap.get(XmlConstants.ELEMENT_NAME_KEY);
            if (StringUtils.hasText(elementName)) {
                // 根据标签名定位业务数据元素
                return parseByElementName(doc, builder, metaMap, elementName);
            } else {
                // 整个XML即为业务数据
                Element rootElement = doc.getDocumentElement();
                return wrapAsMapIfNeeded(parseElement(metaMap, rootElement));
            }
        } catch (Exception e) {
            log.error("解析XML失败:", e);
            throw new RuntimeException("解析XML失败: " + e.getMessage(), e);
        } finally {
            parserFactory.clearThreadLocal();
        }
    }

    /**
     * 根据标签名定位并解析业务数据
     *
     * 配置项（相互独立）：
     * 1. elementName：外层标签定位
     * 2. isCDATA：是否从 CDATA/文本内容中提取（默认 true）
     * 3. dataType：内容类型（json/xml）
     * 4. innerElementName：XML 内容中的二级标签定位
     */
    private static Map<String, Object> parseByElementName(Document doc, DocumentBuilder builder,
                                                           Map<String, Object> metaMap, String elementName) throws Exception {
        NodeList bizDataNodes = doc.getElementsByTagName(elementName);
        if (bizDataNodes.getLength() != 1) {
            log.error("标签名[{}]对应的节点数量为{}，无法确定唯一业务数据元素", elementName, bizDataNodes.getLength());
            throw new RuntimeException("无法确定唯一的业务数据元素，请检查标签名是否正确");
        }

        Node targetNode = bizDataNodes.item(0);

        // 1. 获取业务数据内容 (根据 isCDATA 配置)
        Boolean isCDATAObj = (Boolean) metaMap.get(XmlConstants.IS_CDATA_KEY);
        boolean isCDATA = isCDATAObj == null || isCDATAObj;

        String dataContent;
        if (isCDATA) {
            // CDATA 或文本内容
            dataContent = targetNode.getTextContent().trim();
        } else {
            // 直接取第一个子元素的 XML 内容
            Node firstChild = getFirstElementChild(targetNode);
            if (firstChild == null) {
                throw new RuntimeException("标签[" + elementName + "]没有子元素");
            }
            // 如果是元素节点，直接解析
            Element firstElement = (Element) firstChild;
            return wrapAsMapIfNeeded(parseElement(metaMap, firstElement));
        }

        // 2. 根据 dataType 解析内容
        String dataType = (String) metaMap.get(XmlConstants.DATA_TYPE_KEY);
        if (dataType != null && dataType.equalsIgnoreCase(XmlConstants.JSON_KEY)) {
            // JSON 格式，直接返回
            return JsonUtils.toMap(dataContent);
        }

        // 3. XML 格式，解析为 Document
        Document innerDoc = builder.parse(new InputSource(new StringReader(dataContent)));

        // 4. 内层标签定位（可选）
        Element bizDataElement;
        String innerElementName = (String) metaMap.get(XmlConstants.INNER_ELEMENT_NAME_KEY);
        if (StringUtils.hasText(innerElementName)) {
            bizDataElement = locateInnerElement(innerDoc, innerElementName);
        } else {
            bizDataElement = innerDoc.getDocumentElement();
        }

        return wrapAsMapIfNeeded(parseElement(metaMap, bizDataElement));
    }

    /**
     * 获取第一个子元素节点
     */
    private static Node getFirstElementChild(Node parent) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return children.item(i);
            }
        }
        return null;
    }

    /**
     * 如果解析结果是 List，包装成 Map（使用元素名作为 key）
     * 用于确保 parseXmlForConfig 的返回类型始终是 Map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> wrapAsMapIfNeeded(Object parseResult) {
        if (parseResult instanceof Map) {
            return (Map<String, Object>) parseResult;
        }
        // List 或基本类型：包装成 Map，使用元素名的子标签名（如果是简化后的数组）
        // 这种情况说明该元素只有一种子标签且被简化了，需要从元素的第一个子标签获取 key
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("data", parseResult);
        return wrapper;
    }

    /**
     * 在文档中定位内层元素
     */
    private static Element locateInnerElement(Document doc, String innerElementName) {
        NodeList nodes = doc.getElementsByTagName(innerElementName);
        if (nodes.getLength() == 0) {
            throw new RuntimeException("内层标签[" + innerElementName + "]未找到，请检查innerElementName配置是否正确");
        }
        if (nodes.getLength() > 1) {
            log.warn("内层标签[{}]对应节点数量为{}，使用第一个", innerElementName, nodes.getLength());
        }
        return (Element) nodes.item(0);
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
     * 清洗XML字符串：移除标签间的多余空白
     * 注意：不做全局换行符清理，避免破坏 CDATA 内容
     */
    private static String cleanXmlString(String xml) {
        if (xml == null || xml.isEmpty()) {
            return xml;
        }
        // 只移除标签之间的空白（换行、空格、制表符）
        // 这样不会影响 CDATA 内部的内容
        return xml.replaceAll(">\\s+<", "><");
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
     * SOAP响应模板占位符
     */
    private static final String SOAP_RSP_TEMPLATE_KEY = "soapRspTemplate";
    private static final String SOAP_RSP_PLACEHOLDER = "${outputParam}";

    /**
     * 将Map转换为XML字符串，支持自定义根元素名称和SOAP响应模板
     *
     * @param scheduleRsp 需要转换的Map数据
     * @param metaInfo    元信息（包含命名空间、响应模板等配置）
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

            // 构建业务数据XML
            String businessXml = buildBusinessXml(scheduleRsp, metaMap);

            // 检查是否配置了SOAP响应模板
            String soapRspTemplate = (String) metaMap.get(SOAP_RSP_TEMPLATE_KEY);
            if (soapRspTemplate != null && !soapRspTemplate.trim().isEmpty()) {
                // 使用模板包装响应
                return soapRspTemplate.replace(SOAP_RSP_PLACEHOLDER, businessXml);
            }

            return businessXml;
        } catch (Exception e) {
            log.error("构建SOAP响应XML失败", e);
            throw new RuntimeException("构建SOAP响应XML失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建业务数据XML
     */
    private static String buildBusinessXml(Map<String, Object> scheduleRsp, Map<String, Object> metaMap) throws Exception {
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
        addChildElementsToMap(metaContext, childElements, resultMap);

        return resultMap;
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
    @SuppressWarnings("unchecked")
    private static void addChildElementsToMap(Map<String, Object> metaContext,
                                              Map<String, List<Element>> childElements,
                                              Map<String, Object> map) {
        // 父元素下不同子标签名的数量
        int distinctTagCount = childElements.size();

        for (Map.Entry<String, List<Element>> entry : childElements.entrySet()) {
            String childTagName = entry.getKey();
            List<Element> elements = entry.getValue();
            boolean forceArray = shouldTreatAsArray(metaContext, elements, childTagName, distinctTagCount);
            Object value = parseChildElementValue(metaContext, elements, forceArray);

            // 简化：当子元素是 Map，只有一个 key，且该 key 的值是基本类型数组时，直接使用该数组
            // 例如：ItemHisID: {HIS_ID: ["a", "b"]} → ItemHisID: ["a", "b"]
            if (value instanceof Map) {
                Map<String, Object> childMap = (Map<String, Object>) value;
                if (childMap.size() == 1) {
                    Object innerValue = childMap.values().iterator().next();
                    if (innerValue instanceof List) {
                        List<?> list = (List<?>) innerValue;
                        // 只简化基本类型数组（非 Map 数组）
                        if (list.isEmpty() || !(list.get(0) instanceof Map)) {
                            value = innerValue;
                        }
                    }
                }
            }

            map.put(childTagName, value);
        }
    }

    /**
     * 判断是否应该作为数组处理
     * 核心规则：
     * 1. 有多个同名子元素 → 必然是数组
     * 2. 父元素下只有一种子标签名 → 默认是数组（除非配置了objectTag）
     * 3. 父元素下有多种子标签名 → 默认是对象
     *
     * 示例：
     * <ItemHisID>
     *   <HIS_ID>80000055973</HIS_ID>
     * </ItemHisID>
     * ItemHisID 下只有 HIS_ID 一种标签 → HIS_ID 识别为数组
     *
     * <Request>
     *   <patid>123</patid>
     *   <name>张三</name>
     * </Request>
     * Request 下有多种标签 → patid、name 识别为普通值
     */
    @SuppressWarnings("unchecked")
    private static boolean shouldTreatAsArray(Map<String, Object> metaContext,
                                               List<Element> elements,
                                               String tagName,
                                               int distinctTagCount) {
        // 同名元素数量 > 1，必然是数组
        if (elements.size() > 1) {
            return true;
        }

        // 父元素下只有一种子标签名 → 默认作为数组
        if (distinctTagCount == 1) {
            // 检查是否明确配置为对象（优先级最高）
            if (elements.size() == 1 && elements.get(0).getParentNode() instanceof Element) {
                Element parentElement = (Element) elements.get(0).getParentNode();
                String parentTagName = parentElement.getNodeName();

                List<String> objectTagNames = (List<String>) metaContext.get(XmlConstants.OBJECT_TAG_KEY);
                if (objectTagNames != null && objectTagNames.contains(parentTagName)) {
                    return false;
                }
            }
            return true;
        }

        // 父元素下有多种子标签名 → 默认不作为数组
        return false;
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
}
