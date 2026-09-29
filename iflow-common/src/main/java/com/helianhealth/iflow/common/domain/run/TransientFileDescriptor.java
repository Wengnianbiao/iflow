package com.helianhealth.iflow.common.domain.run;

import java.util.LinkedHashMap;
import java.util.Map;

/** 可持久化的临时上传文件元信息；文件内容通过 {@link FileAccess} 按 fileRef 读取。 */
public record TransientFileDescriptor(
        String fileName,
        String contentType,
        long size,
        String fileRef) {

    public Map<String, Object> toInputMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("fileName", fileName);
        value.put("contentType", contentType);
        value.put("size", size);
        value.put("fileRef", fileRef);
        return value;
    }
}
