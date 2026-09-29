package com.helianhealth.iflow.gateway.rest;

import com.helianhealth.iflow.common.domain.run.TransientFileDescriptor;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryFileScopeTest {

    @Test
    void preservesBytesForRepeatedConcurrentReadsAndIsolatesScopes() throws Exception {
        byte[] uploaded = new byte[] {0, 1, 2, (byte) 0xff};
        InMemoryFileScope first = new InMemoryFileScope();
        TransientFileDescriptor descriptor = first.add(
                "payload.bin", "application/octet-stream", uploaded);
        uploaded[0] = 99;

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<byte[]>> reads = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                reads.add(() -> {
                    try (var input = first.open(descriptor.fileRef())) {
                        return input.readAllBytes();
                    }
                });
            }
            for (Future<byte[]> read : executor.invokeAll(reads)) {
                assertArrayEquals(new byte[] {0, 1, 2, (byte) 0xff}, read.get());
            }
        } finally {
            executor.shutdownNow();
        }

        InMemoryFileScope second = new InMemoryFileScope();
        assertThrows(FileNotFoundException.class, () -> second.open(descriptor.fileRef()));
        first.close();
        assertTrue(first.isClosed());
        assertThrows(FileNotFoundException.class, () -> first.open(descriptor.fileRef()));
        second.close();
    }
}
