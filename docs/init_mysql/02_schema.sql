CREATE TABLE IF NOT EXISTS `meta_workflow_def` (
    `route` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
    `version`        INT           NOT NULL DEFAULT 1,
    `latest_version` INT           NOT NULL DEFAULT 1,
    `json_data` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
    `created_on`     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `modified_on`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`route`, `version`),
    KEY `idx_route` (`route`)
);

CREATE TABLE IF NOT EXISTS `workflow` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `created_on`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `modified_on`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `workflow_id`    VARCHAR(255) NOT NULL,
    `correlation_id` VARCHAR(255) NULL,
    `json_data`      LONGTEXT     NOT NULL,
    UNIQUE KEY `uk_workflow_id` (`workflow_id`),
    KEY `idx_correlation_id` (`correlation_id`)
);

CREATE TABLE IF NOT EXISTS `task` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `created_on`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `modified_on` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `task_id`     VARCHAR(255) NOT NULL,
    `json_data`   LONGTEXT     NOT NULL,
    UNIQUE KEY `uk_task_id` (`task_id`)
);

CREATE TABLE IF NOT EXISTS `task_scheduled` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `created_on`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `modified_on` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `workflow_id` VARCHAR(255) NOT NULL,
    `task_key`    VARCHAR(512) NOT NULL,
    `task_id`     VARCHAR(255) NOT NULL,
    UNIQUE KEY `uk_wf_task_key` (`workflow_id`, `task_key`)
);

CREATE TABLE IF NOT EXISTS `workflow_to_task` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `created_on`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `modified_on` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `workflow_id` VARCHAR(255) NOT NULL,
    `task_id`     VARCHAR(255) NOT NULL,
    UNIQUE KEY `uk_wf_task` (`workflow_id`, `task_id`),
    KEY `idx_workflow_id` (`workflow_id`)
);

CREATE TABLE IF NOT EXISTS `workflow_pending` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `created_on`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `modified_on`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `workflow_type` VARCHAR(255) NOT NULL,
    `workflow_id`   VARCHAR(255) NOT NULL,
    UNIQUE KEY `uk_pending` (`workflow_type`, `workflow_id`),
    KEY `idx_workflow_type` (`workflow_type`)
);

CREATE TABLE IF NOT EXISTS `task_in_progress` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `created_on`         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `modified_on`        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `task_def_name`      VARCHAR(255) NOT NULL,
    `task_id`            VARCHAR(255) NOT NULL,
    `workflow_id`        VARCHAR(255) NOT NULL,
    `in_progress_status` TINYINT(1)   NOT NULL DEFAULT 0,
    UNIQUE KEY `uk_task_def_task` (`task_def_name`, `task_id`),
    KEY `idx_task_def_name` (`task_def_name`)
);

-- 新安装及已有数据库均执行此脚本；先建表，再部署带执行索引的应用。
CREATE TABLE IF NOT EXISTS workflow_index (
                                              workflow_id VARCHAR(255) NOT NULL PRIMARY KEY,
    workflow_route VARCHAR(255) NOT NULL,
    workflow_name TEXT NULL,
    workflow_version INT NOT NULL,
    correlation_id VARCHAR(255) NULL,
    update_time BIGINT NOT NULL,
    reason_for_incompletion TEXT NULL,
    failed_task_id VARCHAR(255) NULL,
    status VARCHAR(64) NOT NULL,
    terminal BOOLEAN NOT NULL,
    successful BOOLEAN NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT NOT NULL,
    duration_ms BIGINT NULL,
    KEY idx_workflow_index_time (start_time),
    KEY idx_workflow_index_route_time (workflow_route, start_time),
    KEY idx_workflow_index_correlation (correlation_id),
    KEY idx_workflow_index_status_time (status, start_time)
    );

CREATE TABLE IF NOT EXISTS task_index (
                                          task_id VARCHAR(255) NOT NULL PRIMARY KEY,
    workflow_id VARCHAR(255) NOT NULL,
    workflow_route VARCHAR(255) NOT NULL,
    node_ref VARCHAR(512) NOT NULL,
    reference_task_name VARCHAR(512) NOT NULL,
    task_name TEXT NULL,
    task_type VARCHAR(255) NOT NULL,
    async_task BOOLEAN NULL,
    status VARCHAR(64) NOT NULL,
    terminal BOOLEAN NOT NULL,
    successful BOOLEAN NOT NULL,
    retry_count INT NOT NULL,
    scheduled_time BIGINT NOT NULL,
    actual_start_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT NOT NULL,
    duration_ms BIGINT NULL,
    KEY idx_task_index_workflow (workflow_id),
    KEY idx_task_index_time (start_time),
    KEY idx_task_index_async_time (async_task, start_time),
    KEY idx_task_index_route_time (workflow_route, start_time),
    KEY idx_task_index_status_time (status, start_time),
    KEY idx_task_index_type_time (task_type, start_time)
    );

CREATE TABLE IF NOT EXISTS `workflow_schedule` (
                                                   `name`             VARCHAR(255) NOT NULL,
    `display_name`     VARCHAR(255) NOT NULL,
    `cron_expression`  VARCHAR(255) NOT NULL,
    `zone_id`          VARCHAR(255) NOT NULL,
    `enabled`          BOOLEAN      NOT NULL,
    `workflow_route`   VARCHAR(255) NOT NULL,
    `workflow_version` INT          NULL,
    `input_json`       LONGTEXT     NOT NULL,
    `next_run_at`      BIGINT       NULL,
    PRIMARY KEY (`name`),
    KEY `idx_workflow_schedule_due` (`enabled`, `next_run_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

