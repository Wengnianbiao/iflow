package com.helianhealth.iflow.ai.mcp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * MCP 协议版本常量与协商逻辑（对齐规范 2025-11-25）。
 */
public final class McpProtocolVersions {

    public static final String LATEST = "2025-11-25";

    private static final List<String> SUPPORTED = Collections.unmodifiableList(Arrays.asList(
            "2025-11-25",
            "2025-06-18",
            "2025-03-26",
            "2024-11-05"
    ));

    private McpProtocolVersions() {
    }

    public static List<String> supported() {
        return SUPPORTED;
    }

    public static boolean isSupported(String version) {
        return version != null && SUPPORTED.contains(version);
    }

    /**
     * 协商协议版本：客户端请求的版本若受支持则原样返回，否则返回服务端最新版本。
     */
    public static String negotiate(String requested) {
        if (isSupported(requested)) {
            return requested;
        }
        return LATEST;
    }
}
