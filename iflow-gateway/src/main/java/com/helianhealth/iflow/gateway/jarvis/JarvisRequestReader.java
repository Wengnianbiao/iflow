package com.helianhealth.iflow.gateway.jarvis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Jarvis 只读取 JSON 对象请求体。 */
@Component
public class JarvisRequestReader {

    private final ObjectMapper objectMapper;

    public JarvisRequestReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> read(HttpServletRequest request) {
        try {
            Charset charset = request.getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8 : Charset.forName(request.getCharacterEncoding());
            String rawBody = StreamUtils.copyToString(request.getInputStream(), charset);
            Map<String, Object> body = objectMapper.readValue(rawBody, new TypeReference<>() {});
            if (body == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jarvis 请求体必须为 JSON 对象");
            }
            return Map.of("body", body, "rawBody", rawBody);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jarvis 请求体必须为 JSON 对象", e);
        }
    }
}
