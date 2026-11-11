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
