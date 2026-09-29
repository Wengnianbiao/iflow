package com.helianhealth.iflow.gateway.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@Slf4j
public class OpenApiController {

    private final OpenApiService openApiService;
    private final OpenApiRequestReader requestReader;

    public OpenApiController(OpenApiService openApiService, OpenApiRequestReader requestReader) {
        this.openApiService = openApiService;
        this.requestReader = requestReader;
    }

    @RequestMapping(
            value = "/hl-open-api/**",
            method = RequestMethod.POST,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE}
    )
    @ResponseBody
    public String invokeOpenApi(HttpServletRequest httpRequest) {
        try (OpenApiRequest request = requestReader.read(httpRequest)) {
            return openApiService.invoke(request);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to close Open API request resources", e);
        }
    }

    @RequestMapping(
            value = "/hl-open-api/**",
            method = RequestMethod.GET,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE}
    )
    @ResponseBody
    public String invokeOpenApiGet(HttpServletRequest httpRequest) {
        try (OpenApiRequest request = requestReader.read(httpRequest)) {
            return openApiService.invoke(request);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to close Open API request resources", e);
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> invalidRequest(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", exception.getStatusCode().value(), "message", exception.getReason()));
    }
}
