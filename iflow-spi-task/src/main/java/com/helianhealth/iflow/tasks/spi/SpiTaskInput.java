package com.helianhealth.iflow.tasks.spi;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SPI 任务入参解析。扩展定义与业务入参均来自 WorkflowTask.inputParameters，
 * 不依赖独立配置表。
 */
public final class SpiTaskInput {

    public static final String REMOTE_EXTENSION = "remoteExtension";
    public static final String EXTENSION_MODE = "extensionMode";
    public static final String CLASS_NAME = "className";
    public static final String SOURCE_CODE = "sourceCode";
    public static final String SPI_INPUT = "spiInput";

    public static final String MODE_ONLINE = "ONLINE";
    public static final String MODE_PROJECT = "PROJECT";

    private SpiTaskInput() {
    }

    public static String extractRemoteExtension(Map<String, Object> inputData) {
        if (inputData == null) {
            return null;
        }
        Object value = inputData.get(REMOTE_EXTENSION);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    public static Map<String, Object> extractSpiInput(Map<String, Object> inputData) {
        Object value = inputData == null ? null : inputData.get(SPI_INPUT);
        if (!(value instanceof Map<?, ?> rawInput)) {
            throw new IllegalArgumentException(
                    "SPI inputParameters.spiInput must resolve to a Map");
        }

        Map<String, Object> spiInput = new LinkedHashMap<>();
        rawInput.forEach(
                (key, item) -> {
                    if (!(key instanceof String stringKey)) {
                        throw new IllegalArgumentException(
                                "SPI inputParameters.spiInput keys must be strings");
                    }
                    spiInput.put(stringKey, item);
                });
        return spiInput;
    }

    /**
     * 解析节点内嵌的扩展实现。没有 className/sourceCode 时返回 null，表示直接调用
     * ExtensionLoader 中已有的项目扩展。
     */
    public static ExtensionDefinition extractExtensionDefinition(Map<String, Object> inputData) {
        String remoteExtension = extractRemoteExtension(inputData);
        String className = text(inputData, CLASS_NAME);
        String sourceCode = text(inputData, SOURCE_CODE);
        String mode = text(inputData, EXTENSION_MODE);

        boolean hasImplementation = className != null || sourceCode != null;
        if (!hasImplementation) {
            if (MODE_ONLINE.equalsIgnoreCase(mode)) {
                throw new IllegalArgumentException(
                        "SPI ONLINE implementation requires className and sourceCode");
            }
            return null;
        }
        if (remoteExtension == null) {
            throw new IllegalArgumentException(
                    "SPI inputParameters.remoteExtension is required for an embedded implementation");
        }
        if ("default".equals(remoteExtension)) {
            throw new IllegalArgumentException(
                    "SPI inputParameters.remoteExtension 'default' is reserved");
        }
        if (mode == null) {
            mode = MODE_ONLINE;
        } else {
            mode = mode.toUpperCase(java.util.Locale.ROOT);
        }
        if (!MODE_ONLINE.equals(mode) && !MODE_PROJECT.equals(mode)) {
            throw new IllegalArgumentException(
                    "SPI inputParameters.extensionMode must be ONLINE or PROJECT");
        }
        if (className == null) {
            throw new IllegalArgumentException("SPI inputParameters.className is required");
        }
        if (MODE_ONLINE.equals(mode) && sourceCode == null) {
            throw new IllegalArgumentException(
                    "SPI inputParameters.sourceCode is required in ONLINE mode");
        }
        return new ExtensionDefinition(remoteExtension, mode, className, sourceCode);
    }

    private static String text(Map<String, Object> inputData, String key) {
        Object value = inputData == null ? null : inputData.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    public record ExtensionDefinition(
            String remoteExtension, String mode, String className, String sourceCode) {

        public boolean online() {
            return MODE_ONLINE.equals(mode);
        }
    }
}
