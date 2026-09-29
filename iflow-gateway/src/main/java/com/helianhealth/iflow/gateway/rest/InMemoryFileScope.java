package com.helianhealth.iflow.gateway.rest;

import com.helianhealth.iflow.common.domain.run.FileAccess;
import com.helianhealth.iflow.common.domain.run.TransientFileDescriptor;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** 一次 Open API 同步调用拥有的原始上传字节作用域；不识别或转换文件内容。 */
final class InMemoryFileScope implements FileAccess, AutoCloseable {

    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    TransientFileDescriptor add(String fileName, String contentType, byte[] bytes) {
        if (closed.get()) {
            throw new IllegalStateException("Temporary file scope is closed");
        }
        String ref = "upload-" + UUID.randomUUID();
        files.put(ref, bytes.clone());
        return new TransientFileDescriptor(fileName, contentType, bytes.length, ref);
    }

    @Override
    public InputStream open(String fileRef) throws FileNotFoundException {
        if (closed.get()) {
            throw new FileNotFoundException("Temporary file scope is closed");
        }
        byte[] bytes = files.get(fileRef);
        if (bytes == null) {
            throw new FileNotFoundException("Temporary file reference is unknown: " + fileRef);
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            files.clear();
        }
    }

    boolean isClosed() {
        return closed.get();
    }
}
