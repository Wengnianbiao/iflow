package com.helianhealth.agent.repository.model.def;

import com.helianhealth.agent.common.enums.NodeType;
import com.helianhealth.agent.common.enums.ScheduleParamSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流节点定义（一次三方接口外呼的协议 + 调度 + 参数映射）。
 *
 * <p>对应原 {@code interface_workflow_node} 表的一行，连同其 {@code node_param_config}（PRE/POST 映射）
 * 一并内嵌，整体随 {@link WorkflowDef} 序列化进 {@code json_data}。
 *
 * <p><b>引用约定</b>：节点之间不再依赖全局自增 {@code node_id}，而是以 {@link #name}（工作流内唯一）作为引用键，
 * 与 conductor 的 {@code taskReferenceName} 范式一致。{@link WorkflowDef#getFirstNodes()} 与调度的下一跳
 * 均以节点 {@code name} 引用，使整份定义自包含、可整体复制/版本化。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeDef {

    /**
     * 节点引用名（工作流内唯一），作为 DAG 引用键
     */
    private String name;

    /**
     * 节点展示名
     */
    private String displayName;

    /**
     * 节点厂商/协议类型：HTTP | WEBSERVICE | DATABASE | NONE
     */
    private NodeType nodeType;

    /**
     * 协议元数据（JSON 字符串），按 nodeType 含 url/headers/sqlTemplate 等
     */
    private String metaInfo;

    /**
     * 入参过滤表达式（SpEL，仅 #data；结果为空则跳过本节点外呼）
     */
    private String paramFilterExpr;

    /**
     * 数组拆分字段，按片并行外呼后 merge
     */
    private String splitField;

    /**
     * 条件调度表达式（SpEL；返回下一跳节点引用名集合或 null）
     */
    private String scheduleExpr;

    /**
     * 调度参数来源：ORIGINAL 用最初入参 / PRE_RESPONSE 用本节点响应
     */
    private ScheduleParamSourceType scheduleParamSourceType;

    /**
     * 日志关键字来源字段
     */
    private String keywordField;

    /**
     * 远程调用 SPI 扩展名（InterfaceRemoteInvoker）
     */
    private String remoteExtension;

    /**
     * 通用扩展参数（过滤器链 / Mock 等），如 filter.mock=123
     */
    @Builder.Default
    private Map<String, String> extensionParam = new HashMap<>();

    /**
     * 前置映射（上游入参 → 下游请求体），树形
     */
    @Builder.Default
    private List<ParamMappingDef> preParams = new ArrayList<>();

    /**
     * 后置映射（下游响应 → 上游响应），树形
     */
    @Builder.Default
    private List<ParamMappingDef> postParams = new ArrayList<>();
}
