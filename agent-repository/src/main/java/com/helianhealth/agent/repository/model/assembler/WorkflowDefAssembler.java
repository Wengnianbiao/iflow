package com.helianhealth.agent.repository.model.assembler;

import com.alibaba.fastjson2.JSON;
import com.helianhealth.agent.common.enums.WorkflowContentType;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.repository.model.def.WorkflowDef;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowDefPO;

/**
 * {@link WorkflowDef} 与 {@link InterfaceWorkflowDefPO} 的双向转换器。
 *
 * <p>核心职责：在持久化边界对 {@code json_data} 做整份工作流定义的序列化 / 反序列化，
 * 并把路由查询所需的 {@code name / interface_uri / status} 等冗余列从定义中抽出回填到 PO。
 */
public final class WorkflowDefAssembler {

    private WorkflowDefAssembler() {
    }

    /**
     * PO → 领域定义：从 {@code json_data} 反序列化出完整 {@link WorkflowDef}。
     */
    public static WorkflowDef toDomain(InterfaceWorkflowDefPO po) {
        if (po == null || po.getJsonData() == null) {
            return null;
        }
        return JSON.parseObject(po.getJsonData(), WorkflowDef.class);
    }

    /**
     * 领域定义 → PO：把整份定义序列化进 {@code json_data}，并抽出冗余索引列。
     *
     * <p>不在此处维护 {@code latest_version}（由仓储层在事务内按版本语义统一维护）。
     */
    public static InterfaceWorkflowDefPO toPO(WorkflowDef def) {
        if (def == null) {
            return null;
        }
        return InterfaceWorkflowDefPO.builder()
                .name(def.getName())
                .version(def.getVersion())
                .interfaceUri(def.getInterfaceUri())
                .status(def.getStatus())
                .jsonData(JsonUtils.toJsonString(def))
                .build();
    }

    /**
     * 便捷方法：内容类型枚举名转换（容错大小写），供上层装配使用。
     */
    public static WorkflowContentType parseContentType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return WorkflowContentType.valueOf(value.trim().toUpperCase());
    }
}
