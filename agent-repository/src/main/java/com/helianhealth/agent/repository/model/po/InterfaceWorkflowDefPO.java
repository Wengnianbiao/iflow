package com.helianhealth.agent.repository.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

/**
 * 工作流定义持久化对象（conductor {@code meta_workflow_def} 范式的业务适配版）。
 *
 * <p>核心三列：{@code name} + {@code version} 唯一标识一份定义，{@code json_data} 承载整份工作流配置
 * （{@link com.helianhealth.agent.repository.model.def.WorkflowDef} 的 JSON 序列化）。
 * {@code latest_version} 用于快速取最新版（与 conductor 一致）。
 *
 * <p>业务适配：将路由键 {@code interface_uri} 与启用态 {@code status} 冗余为独立索引列，
 * 以支撑入口「按 URI 命中最新启用版本」的高频查询；其权威来源仍是 {@code json_data} 内的定义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "interface_workflow_def")
public class InterfaceWorkflowDefPO {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "version")
    private Integer version;

    @Column(name = "latest_version")
    private Integer latestVersion;

    @Column(name = "interface_uri")
    private String interfaceUri;

    @Column(name = "status")
    private Integer status;

    @Column(name = "json_data")
    private String jsonData;

    @Column(name = "created_on")
    private Date createdOn;

    @Column(name = "modified_on")
    private Date modifiedOn;
}
