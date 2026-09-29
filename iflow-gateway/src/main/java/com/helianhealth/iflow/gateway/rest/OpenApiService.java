package com.helianhealth.iflow.gateway.rest;

/**
 * Open API 入站服务。
 */
public interface OpenApiService {

    String invoke(OpenApiRequest request);
}
