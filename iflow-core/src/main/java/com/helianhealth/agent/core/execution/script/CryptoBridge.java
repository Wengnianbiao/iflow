package com.helianhealth.agent.core.execution.script;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * GraalJS 脚本内可用的摘要函数。{@code Crypto.md5(value)} 按 UTF-8 计算，返回小写 32 位十六进制。
 */
public class CryptoBridge {

    public String md5(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        try {
            byte[] hash =
                    MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is not available", e);
        }
    }
}
