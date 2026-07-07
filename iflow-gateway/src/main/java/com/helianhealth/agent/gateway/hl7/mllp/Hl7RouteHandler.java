package com.helianhealth.agent.gateway.hl7.mllp;

import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.agent.common.extension.AgentAdaptive;
import com.helianhealth.agent.common.extension.AgentSPI;

/**
 * HL7 v2 MLLP 入站扩展点（预留）。
 */
@AgentSPI("default")
public interface Hl7RouteHandler {

    @AgentAdaptive("routeExtension")
    String handle(WorkflowDef definition, String rawMessage);
}
