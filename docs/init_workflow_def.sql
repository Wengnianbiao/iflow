-- ============================================================================
-- 工作流定义表（conductor meta_workflow_def 范式的业务适配版）
--
-- 「配置即工作流」：一份工作流的全部配置（入口路由、内容适配、DAG 首跳、全部节点
-- 及其 PRE/POST 参数映射）整体序列化为 JSON 存入 json_data，以 (name, version) 唯一
-- 标识并版本化；latest_version 用于快速命中最新版。
--
-- 业务适配：将路由键 interface_uri 与启用态 status 冗余为独立索引列，支撑入口
-- 「按 URI 命中最新启用版本」的高频查询；其权威来源仍是 json_data 内的定义。
-- ============================================================================
CREATE TABLE `interface_workflow_def` (
    -- 主键ID，自增
    `id`             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- 工作流定义名（业务唯一名），与 version 共同唯一标识一份定义
    `name`           VARCHAR(150)  NOT NULL,
    -- 版本号，从 1 起递增
    `version`        INT           NOT NULL DEFAULT 1,
    -- 最新版本号（该 name 下所有行取相同值），version = latest_version 即为最新版
    `latest_version` INT           NOT NULL DEFAULT 0,
    -- 入口接口 URI（路由键，冗余列）
    `interface_uri`  VARCHAR(150)  NULL,
    -- 启用态：1 启用 / 0 禁用（仅启用版本参与路由命中）
    `status`         INT           NOT NULL DEFAULT 1,
    -- 整份工作流配置（WorkflowDef 的 JSON 序列化）
    `json_data`      LONGTEXT      NOT NULL,
    -- 创建时间
    `created_on`     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 修改时间
    `modified_on`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- name + version 唯一
    UNIQUE KEY `uk_name_version` (`name`, `version`),
    -- 按 name 查询
    KEY `idx_name` (`name`),
    -- 按入口 URI 路由命中
    KEY `idx_interface_uri` (`interface_uri`)
);
