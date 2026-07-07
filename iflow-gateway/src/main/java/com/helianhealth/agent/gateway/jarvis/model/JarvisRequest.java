package com.helianhealth.agent.gateway.jarvis.model;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class JarvisRequest {

    @JsonProperty(value = "BusinessMethod")
    private String businessMethod;

    @JsonProperty(value = "BusinessCode")
    private String businessCode;

    @JsonProperty(value = "data")
    private JSONObject data;

    @JsonProperty(value = "ServiceProviderType")
    private String serviceProviderType;

    @JsonProperty(value = "PatientCode")
    private String patientCode;

    @JsonProperty(value = "OutFeeItemIdList")
    private List<String> outFeeItemIdList;

    @JsonProperty(value = "ApplyNoList")
    private List<String> applyNoList;

    @JsonProperty(value = "BarCodeList")
    private List<String> barCodeList;

    @JsonProperty(value = "AuditTime")
    private String auditTime;

    @JsonProperty(value = "QueryType")
    private Integer queryType;
}
