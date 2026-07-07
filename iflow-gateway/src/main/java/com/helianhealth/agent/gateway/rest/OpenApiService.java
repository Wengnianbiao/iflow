package com.helianhealth.agent.gateway.rest;

/**
 * Open API 入站服务。
 */
public interface OpenApiService {

    String invoke(String requestBody, String routeKey);
}
