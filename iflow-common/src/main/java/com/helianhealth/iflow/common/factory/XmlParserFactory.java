package com.helianhealth.iflow.common.factory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * XML解析器单例工厂（线程安全）
 */
public class XmlParserFactory {

    private static final XmlParserFactory INSTANCE = new XmlParserFactory();

    // 线程安全的DocumentBuilder
    private final ThreadLocal<DocumentBuilder> documentBuilderThreadLocal = ThreadLocal.withInitial(() -> {
        try {
            return getDocumentBuilderFactory().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("创建DocumentBuilder失败", e);
        }
    });

    private final DocumentBuilderFactory documentBuilderFactory;

    private XmlParserFactory() {
        documentBuilderFactory = DocumentBuilderFactory.newInstance();
        // 全局启用命名空间支持
        documentBuilderFactory.setNamespaceAware(true);
    }

    public static XmlParserFactory getInstance() {
        return INSTANCE;
    }

    private DocumentBuilderFactory getDocumentBuilderFactory() {
        return documentBuilderFactory;
    }

    /**
     * 获取当前线程的DocumentBuilder（线程隔离）
     */
    public DocumentBuilder getDocumentBuilder() {
        return documentBuilderThreadLocal.get();
    }

    /**
     * 清理当前线程的DocumentBuilder
     */
    public void clearThreadLocal() {
        documentBuilderThreadLocal.remove();
    }
}
