package com.helianhealth.iflow.core.execution.script;

import com.helianhealth.iflow.common.domain.run.FileAccess;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Base64;

/** GraalJS 内显式读取当前工作流临时文件的桥。 */
public final class FilesBridge {

    private final FileAccess fileAccess;

    public FilesBridge(FileAccess fileAccess) {
        this.fileAccess = fileAccess;
    }

    public String readText(String fileRef, String charsetName) {
        Charset charset;
        try {
            charset = Charset.forName(charsetName);
        } catch (Exception e) {
            throw new IllegalArgumentException("Files.readText charset is invalid: " + charsetName);
        }
        byte[] bytes = read(fileRef);
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Files.readText failed: invalid " + charset.name() + " content");
        }
    }

    public String toBase64(String fileRef) {
        return Base64.getEncoder().encodeToString(read(fileRef));
    }

    private byte[] read(String fileRef) {
        if (fileAccess == null) {
            throw new IllegalStateException("Temporary file access is not available for this workflow");
        }
        try (InputStream input = fileAccess.open(fileRef)) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Temporary file is unavailable: " + fileRef);
        }
    }
}
