package com.helianhealth.iflow.gateway.rest;

import com.helianhealth.iflow.common.utils.JsonUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 在 multipart 解析发生前统计 Open API 请求流的实际字节数。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OpenApiBodyLimitFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;

    public OpenApiBodyLimitFilter(
            @Value("${iflow.open-api.max-body-bytes:10485760}") long maxBodyBytes) {
        if (maxBodyBytes <= 0) {
            throw new IllegalArgumentException("iflow.open-api.max-body-bytes must be positive");
        }
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.equals("/hl-open-api") || path.startsWith("/hl-open-api/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBodyBytes) {
            writeTooLarge(response);
            return;
        }
        try {
            filterChain.doFilter(new CountingRequest(request, maxBodyBytes), response);
        } catch (Exception e) {
            if (hasSizeCause(e) && !response.isCommitted()) {
                writeTooLarge(response);
                return;
            }
            throw e;
        }
    }

    private static boolean hasSizeCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof OpenApiPayloadTooLargeException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void writeTooLarge(HttpServletResponse response) throws IOException {
        response.reset();
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JsonUtils.toJsonString(
                Map.of("code", HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "message", "请求体超过大小限制")));
    }

    private static final class CountingRequest extends HttpServletRequestWrapper {
        private final long limit;
        private CountingInputStream stream;
        private BufferedReader reader;

        private CountingRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (reader != null) {
                throw new IllegalStateException("getReader() has already been called");
            }
            if (stream == null) {
                stream = new CountingInputStream(super.getInputStream(), limit);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (reader == null) {
                Charset charset = getCharacterEncoding() == null
                        ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
                reader = new BufferedReader(new InputStreamReader(getInputStream(), charset));
            }
            return reader;
        }
    }

    private static final class CountingInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long limit;
        private long count;

        private CountingInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                add(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = delegate.read(bytes, offset, length);
            if (read > 0) {
                add(read);
            }
            return read;
        }

        @Override
        public long skip(long amount) throws IOException {
            long skipped = delegate.skip(amount);
            if (skipped > 0) {
                add(skipped);
            }
            return skipped;
        }

        private void add(long bytes) throws OpenApiPayloadTooLargeException {
            count += bytes;
            if (count > limit) {
                throw new OpenApiPayloadTooLargeException();
            }
        }

        @Override public boolean isFinished() { return delegate.isFinished(); }
        @Override public boolean isReady() { return delegate.isReady(); }
        @Override public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
        @Override public void close() throws IOException { delegate.close(); }
    }
}
