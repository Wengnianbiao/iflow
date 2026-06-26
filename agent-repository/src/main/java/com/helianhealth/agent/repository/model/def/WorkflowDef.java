package com.helianhealth.agent.repository.model.def;

import com.helianhealth.agent.common.enums.WorkflowContentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流定义（conductor {@code WorkflowDef} 范式的业务适配版）。
 *
 * <p>这是「配置即工作流」的核心载体：一份 {@code WorkflowDef} 完整描述一条工作流的入口路由、内容适配、
 * DAG 首跳以及全部节点（含协议、调度、PRE/POST 参数映射）。它整体序列化为 JSON 后存入
 * {@code interface_workflow_def.json_data}，并以 {@code (name, version)} 唯一标识与版本化。
 *
 * <p>相对原三层关系模型（{@code interface_workflow} + {@code interface_workflow_node} +
 * {@code node_param_config}）的变化：
 * <ul>
 *   <li>不再使用分表与全局自增主键关联，所有配置内嵌为一棵树；</li>
 *   <li>节点间引用由全局 {@code node_id} 改为工作流内唯一的节点 {@code name}（见 {@link NodeDef}）；</li>
 *   <li>入口路由键 {@code interfaceUri} 与启用态 {@code status} 仍可冗余到表列以支撑路由查询，
 *       但权威来源是本对象。</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDef {

    /**
     * 工作流定义名（业务唯一名），与 {@link #version} 共同唯一标识一份定义
     */
    private String name;

    /**
     * 版本号，从 1 起递增
     */
    @Builder.Default
    private int version = 1;

    /**
     * 描述
     */
    private String description;

    /**
     * 入口接口 URI（路由键）。Open API 用完整请求 URI；Jarvis 用 businessMethod。
     */
    private String interfaceUri;

    /**
     * 入口路由扩展名（WORKFLOW / JARVIS_WORKFLOW 类扩展），自定义入口编排
     */
    private String routeExtension;

    /**
     * 内容类型：JSON | XML，决定请求体↔Map、响应 Map↔字符串的适配方式
     */
    private WorkflowContentType contentType;

    /**
     * 内容元信息（主要为 XML 请求解析 + 响应拼装的 JSON 配置）
     */
    private String contentMetaInfo;

    /**
     * 首跳节点引用名列表（可多个，并行执行）
     */
    @Builder.Default
    private List<String> firstNodes = new ArrayList<>();

    /**
     * 全部节点定义
     */
    @Builder.Default
    private List<NodeDef> nodes = new ArrayList<>();

    /**
     * 启用态：1 启用 / 0 禁用（仅启用的工作流参与路由命中）
     */
    @Builder.Default
    private int status = 1;

    /**
     * {@code name.version} 形式的复合键，便于缓存与日志。
     */
    public String key() {
        return getKey(name, version);
    }

    public static String getKey(String name, int version) {
        return name + "." + version;
    }

    /**
     * 按引用名查找节点。
     */
    public NodeDef getNodeByName(String nodeName) {
        if (nodeName == null || nodes == null) {
            return null;
        }
        return nodes.stream()
                .filter(n -> nodeName.equals(n.getName()))
                .findFirst()
                .orElse(null);
    }
}
