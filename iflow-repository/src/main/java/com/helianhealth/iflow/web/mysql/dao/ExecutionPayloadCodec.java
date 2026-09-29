package com.helianhealth.iflow.web.mysql.dao;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 执行记录 JSON 的落库编码。
 *
 * <p>OceanBase 更新超过大约 8MB 的 {@code LONGTEXT} 时，耗时会从数秒跳到几十秒，并撞上默认 10 秒的
 * {@code ob_query_timeout}。超过阈值的报文先 gzip，再以 {@code iflow-gz1:} 前缀写入；旧的纯 JSON 行原样读取。
 */
final class ExecutionPayloadCodec {

    static final String PREFIX = "iflow-gz1:";

    /** 超过该 UTF-8 字节数才压缩，并在压缩后仍然很大时拉长单条写入超时。 */
    static final int LARGE_WRITE_BYTES = 4 * 1024 * 1024;

    private static final int DEFAULT_MAX_DECODED_BYTES = 128 * 1024 * 1024;

    private final int thresholdBytes;
    private final int maxDecodedBytes;

    ExecutionPayloadCodec() {
        this(LARGE_WRITE_BYTES, DEFAULT_MAX_DECODED_BYTES);
    }

    ExecutionPayloadCodec(int thresholdBytes, int maxDecodedBytes) {
        if (thresholdBytes <= 0 || maxDecodedBytes <= 0) {
            throw new IllegalArgumentException("payload limits must be positive");
        }
        this.thresholdBytes = thresholdBytes;
        this.maxDecodedBytes = maxDecodedBytes;
    }

    String encode(String json) {
        if (json == null || utf8ByteLength(json) < thresholdBytes) {
            return json;
        }
        byte[] raw = json.getBytes(StandardCharsets.UTF_8);
        String encoded = PREFIX + Base64.getEncoder().encodeToString(gzip(raw));
        if (encoded.length() >= raw.length) {
            return json;
        }
        return encoded;
    }

    String decode(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] compressed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            return new String(gunzip(compressed), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Could not read compressed execution payload", ex);
        }
    }

    static int utf8ByteLength(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int bytes = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x7F) {
                bytes++;
            } else if (c <= 0x7FF) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c)
                    && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) {
                bytes += 4;
                i++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    private static byte[] gzip(byte[] raw) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(raw.length, 64 * 1024));
        try (FastGzipOutputStream gzip = new FastGzipOutputStream(buffer)) {
            gzip.write(raw);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not compress execution payload", ex);
        }
        return buffer.toByteArray();
    }

    private byte[] gunzip(byte[] compressed) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buf)) >= 0) {
                total += read;
                if (total > maxDecodedBytes) {
                    throw new IllegalStateException(
                            "Execution payload exceeds " + maxDecodedBytes + " bytes after decompression");
                }
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read compressed execution payload", ex);
        }
    }

    private static final class FastGzipOutputStream extends GZIPOutputStream {
        private FastGzipOutputStream(ByteArrayOutputStream out) throws IOException {
            super(out);
            def.setLevel(Deflater.BEST_SPEED);
        }
    }
}
