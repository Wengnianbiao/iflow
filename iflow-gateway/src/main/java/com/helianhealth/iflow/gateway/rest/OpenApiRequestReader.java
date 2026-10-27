package com.helianhealth.iflow.gateway.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.utils.JsonValueCodec;
import com.helianhealth.iflow.common.utils.XmlMapParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 在启动工作流前完成体积检查、HTTP 拆包及文本报文解析。上传文件原始字节以 Base64 作为普通入参传递。 */
@Component
public class OpenApiRequestReader {

    public static final int DEFAULT_MAX_BODY_BYTES = 10 * 1024 * 1024;
    private final JsonValueCodec jsonCodec;
    private final int maxBodyBytes;

    public OpenApiRequestReader(
            ObjectMapper mapper,
            @Value("${iflow.open-api.max-body-bytes:10485760}") int maxBodyBytes) {
        if (maxBodyBytes <= 0) {
            throw new IllegalArgumentException("iflow.open-api.max-body-bytes must be positive");
        }
        this.jsonCodec = new JsonValueCodec(mapper);
        this.maxBodyBytes = maxBodyBytes;
    }

    public OpenApiRequest read(HttpServletRequest request) {
        Map<String, Object> query = parseUrlEncoded(request.getQueryString(), StandardCharsets.UTF_8);
        Map<String, Object> headers = headers(request);
        try {
            if ("GET".equalsIgnoreCase(request.getMethod())) {
                // GET 不把请求体交给工作流，但仍读取并执行实际字节大小限制。
                readLimited(request.getInputStream(), request.getContentLengthLong(), 0);
                return result(request, null, query, headers, Map.of(), "");
            }

            String contentType = request.getContentType();
            MediaType mediaType = isMultipart(contentType) ? optionalContentType(contentType) : null;
            if (mediaType != null) {
                if (!MediaType.MULTIPART_FORM_DATA.isCompatibleWith(mediaType)) {
                    throw unsupportedMediaType();
                }
                if (mediaType.getParameter("boundary") == null
                        || mediaType.getParameter("boundary").isBlank()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "multipart/form-data 缺少 boundary");
                }
                return readMultipart(request, query, headers);
            }

            byte[] bytes = readLimited(request.getInputStream(), request.getContentLengthLong(), 0);
            if (bytes.length == 0) {
                return result(request, null, query, headers, Map.of(), "");
            }
            if (mediaType == null) {
                mediaType = optionalContentType(contentType);
            }
            if (mediaType == null) {
                throw unsupportedMediaType();
            }
            ParsedBody parsed = readTextBody(bytes, mediaType);
            return result(request, parsed.body(), query, headers, Map.of(), parsed.rawText());
        } catch (OpenApiPayloadTooLargeException e) {
            throw tooLarge();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取请求体", e);
        }
    }

    private OpenApiRequest readMultipart(
            HttpServletRequest request,
            Map<String, Object> query,
            Map<String, Object> headers) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        Map<String, Object> files = new LinkedHashMap<>();
        long total = 0;
        try {
            Collection<Part> parts = request.getParts();
            for (Part part : parts) {
                if (part.getName() == null || part.getName().isBlank()) {
                    throw new IllegalArgumentException("multipart part name is missing");
                }
                byte[] bytes = readLimited(part.getInputStream(), part.getSize(), total);
                total += bytes.length;
                String fileName = part.getSubmittedFileName();
                if (fileName == null) {
                    Charset charset = partCharset(part, request);
                    add(fields, part.getName(), decode(bytes, charset));
                    continue;
                }
                if (fileName.isBlank() && bytes.length == 0) {
                    continue;
                }
                Map<String, Object> file = new LinkedHashMap<>();
                file.put("fileName", fileName);
                file.put("contentType", emptyToNull(part.getContentType()));
                file.put("size", (long) bytes.length);
                file.put("contentBase64", Base64.getEncoder().encodeToString(bytes));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> values = (List<Map<String, Object>>) files.computeIfAbsent(
                        part.getName(), ignored -> new ArrayList<Map<String, Object>>());
                values.add(file);
            }
        } catch (OpenApiPayloadTooLargeException e) {
            throw tooLarge();
        } catch (IOException | ServletException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "multipart/form-data 请求不合法", e);
        }
        return result(request, compact(fields), query, headers, files, null);
    }

    private ParsedBody readTextBody(byte[] bytes, MediaType mediaType) {
        String type = mediaType.getType();
        String subtype = mediaType.getSubtype();
        boolean json = "application".equals(type)
                && ("json".equals(subtype) || subtype.endsWith("+json"));
        boolean xml = ("application".equals(type)
                && ("xml".equals(subtype) || subtype.endsWith("+xml")))
                || MediaType.TEXT_XML.isCompatibleWith(mediaType);
        boolean text = MediaType.TEXT_PLAIN.isCompatibleWith(mediaType);
        boolean form = MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(mediaType);
        if (!json && !xml && !text && !form) {
            throw unsupportedMediaType();
        }

        try {
            Charset explicitCharset = mediaType.getCharset();
            String decoded = xml
                    ? XmlMapParser.decodeBytes(bytes,
                            explicitCharset == null ? null : explicitCharset.name())
                    : decode(bytes, explicitCharset == null ? StandardCharsets.UTF_8 : explicitCharset);
            if (text) {
                return new ParsedBody(decoded, decoded);
            }
            if (form) {
                Charset charset = explicitCharset == null ? StandardCharsets.UTF_8 : explicitCharset;
                return new ParsedBody(parseUrlEncoded(decoded, charset), decoded);
            }
            if (xml) {
                return new ParsedBody(XmlMapParser.parseBytes(
                        bytes, explicitCharset == null ? null : explicitCharset.name()).value(), decoded);
            }
            String document = decoded.startsWith("\uFEFF") ? decoded.substring(1) : decoded;
            return new ParsedBody(jsonCodec.parseDocument(document), decoded);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体格式或字符编码不合法", e);
        }
    }

    private OpenApiRequest result(
            HttpServletRequest request,
            Object body,
            Map<String, Object> query,
            Map<String, Object> headers,
            Map<String, Object> files,
            String rawBody) {
        return new OpenApiRequest(body, query, headers, files, request.getMethod(),
                OpenApiPaths.pathWithinApplication(request), rawBody);
    }

    private byte[] readLimited(InputStream input, long reportedLength, long alreadyRead) throws IOException {
        if (reportedLength >= 0 && reportedLength + alreadyRead > maxBodyBytes) {
            throw new OpenApiPayloadTooLargeException();
        }
        int remaining = (int) Math.min(Integer.MAX_VALUE, maxBodyBytes - alreadyRead);
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(8192, Math.max(0, remaining)));
        byte[] buffer = new byte[(int) Math.min(8192L, Math.max(1L, (long) remaining + 1L))];
        long count = alreadyRead;
        int read;
        while ((read = input.read(buffer, 0,
                (int) Math.min(buffer.length, maxBodyBytes - count + 1))) != -1) {
            count += read;
            if (count > maxBodyBytes) {
                throw new OpenApiPayloadTooLargeException();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private MediaType optionalContentType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(value);
            if (!mediaType.isConcrete()) {
                throw unsupportedMediaType();
            }
            mediaType.getCharset();
            return mediaType;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw unsupportedMediaType();
        }
    }

    private static boolean isMultipart(String contentType) {
        return contentType != null
                && contentType.trim().toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    private static Charset partCharset(Part part, HttpServletRequest request) {
        String contentType = part.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            MediaType partType = MediaType.parseMediaType(contentType);
            if (partType.getCharset() != null) {
                return partType.getCharset();
            }
        }
        String requestEncoding = request.getCharacterEncoding();
        return requestEncoding == null ? StandardCharsets.UTF_8 : Charset.forName(requestEncoding);
    }

    private static Map<String, Object> parseUrlEncoded(String encoded, Charset charset) {
        if (encoded == null || encoded.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String pair : encoded.split("&", -1)) {
            int separator = pair.indexOf('=');
            String name = separator < 0 ? pair : pair.substring(0, separator);
            String value = separator < 0 ? "" : pair.substring(separator + 1);
            try {
                add(values, URLDecoder.decode(name, charset), URLDecoder.decode(value, charset));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "表单或查询参数编码不合法", e);
            }
        }
        return compact(values);
    }

    private static Map<String, Object> headers(HttpServletRequest request) {
        Map<String, Object> headers = new LinkedHashMap<>();
        if (request.getHeaderNames() == null) {
            return headers;
        }
        Collections.list(request.getHeaderNames()).forEach(name ->
                headers.put(name.toLowerCase(Locale.ROOT),
                        compactStrings(Collections.list(request.getHeaders(name)))));
        return headers;
    }

    private static void add(Map<String, List<String>> values, String name, String value) {
        values.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
    }

    private static Map<String, Object> compact(Map<String, List<String>> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, items) -> result.put(key, compactStrings(items)));
        return result;
    }

    private static Object compactStrings(List<String> values) {
        return values.size() == 1 ? values.get(0) : List.copyOf(values);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String decode(byte[] bytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static ResponseStatusException unsupportedMediaType() {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "非空请求体必须声明支持的 Content-Type");
    }

    private static ResponseStatusException tooLarge() {
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "请求体超过大小限制");
    }

    private record ParsedBody(Object body, String rawText) {}
}
