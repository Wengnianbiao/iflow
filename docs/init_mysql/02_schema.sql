CREATE TABLE IF NOT EXISTS `meta_workflow_def` (
    `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
    `version`        INT           NOT NULL DEFAULT 1,
    `latest_version` INT           NOT NULL DEFAULT 1,
    `json_data` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL,
    `created_on`     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `modified_on`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`name`, `version`),
    KEY `idx_name` (`name`)
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
