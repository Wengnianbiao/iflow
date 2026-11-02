package com.helianhealth.iflow.persistence.mysql.dao;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionPayloadCodecTest {

    @Test
    void smallPayloadStaysPlainJson() {
        ExecutionPayloadCodec codec = new ExecutionPayloadCodec();

        assertEquals("{\"a\":1}", codec.encode("{\"a\":1}"));
        assertEquals("{\"a\":1}", codec.decode("{\"a\":1}"));
        assertNull(codec.decode(null));
    }

    @Test
    void repetitivePayloadShrinksAndRoundTrips() {
        ExecutionPayloadCodec codec = new ExecutionPayloadCodec(64, 1024 * 1024);
        String json = "{\"items\":\"" + "service-item-".repeat(80) + "\"}";

        String stored = codec.encode(json);

        assertTrue(stored.startsWith(ExecutionPayloadCodec.PREFIX));
        assertTrue(stored.length() < json.length());
        assertEquals(json, codec.decode(stored));
    }

    @Test
    void incompressiblePayloadStaysPlain() {
        ExecutionPayloadCodec codec = new ExecutionPayloadCodec(8, 1024 * 1024);
        String json = "abcdefghij";

        assertEquals(json, codec.encode(json));
    }

    @Test
    void rejectsCorruptAndOversizedPayloads() {
        ExecutionPayloadCodec codec = new ExecutionPayloadCodec(1, 32);
        String oversized = codec.encode("a".repeat(200));

        assertTrue(oversized.startsWith(ExecutionPayloadCodec.PREFIX));
        assertThrows(IllegalStateException.class, () -> codec.decode(oversized));
        assertThrows(
                IllegalStateException.class,
                () -> codec.decode(ExecutionPayloadCodec.PREFIX + Base64.getEncoder().encodeToString(new byte[] {1, 2, 3})));
    }

    @Test
    void largeExecutionWriteUsesOceanBaseQueryTimeoutHint() {
        assertEquals(
                "UPDATE task SET json_data = ?",
                MySqlExecutionDAO.withLargeWriteHint("UPDATE task SET json_data = ?", "abc", 4));
        assertEquals(
                "UPDATE /*+ QUERY_TIMEOUT(180000000) */ task SET json_data = ?",
                MySqlExecutionDAO.withLargeWriteHint("UPDATE task SET json_data = ?", "中文", 4));
        assertEquals(
                "INSERT /*+ QUERY_TIMEOUT(180000000) */ INTO task (task_id, json_data) VALUES (?, ?)",
                MySqlExecutionDAO.withLargeWriteHint(
                        "INSERT INTO task (task_id, json_data) VALUES (?, ?)", "中文", 4));
    }

    @Test
    void utf8ByteLengthCountsChineseAndEmoji() {
        assertEquals(0, ExecutionPayloadCodec.utf8ByteLength(null));
        assertEquals(3, ExecutionPayloadCodec.utf8ByteLength("中"));
        assertEquals(6, ExecutionPayloadCodec.utf8ByteLength("中文"));
        assertEquals(4, ExecutionPayloadCodec.utf8ByteLength("😀"));
    }
}
