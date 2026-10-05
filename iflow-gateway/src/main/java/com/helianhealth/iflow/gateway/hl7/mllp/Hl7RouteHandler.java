package com.helianhealth.iflow.gateway.hl7.mllp;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.extension.Adaptive;
import com.helianhealth.iflow.common.extension.SPI;

/**
 * HL7 v2 MLLP 入站扩展点（预留）。
 */
@SPI("default")
public interface Hl7RouteHandler {

    @Adaptive("routeExtension")
    String handle(WorkflowDef definition, String rawMessage);
}
