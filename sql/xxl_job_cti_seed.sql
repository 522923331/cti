USE `xxl_job`;

INSERT INTO `xxl_job_group` (`id`, `app_name`, `title`, `address_type`, `address_list`, `update_time`)
VALUES (2, 'cti-executor', 'CTI', 0, NULL, '2026-01-01 00:00:00');

INSERT INTO `xxl_job_info` (
    `id`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
    `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
    `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
    `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`, `child_jobid`
) VALUES (
    2, 2, 'CTI health probe', '2026-01-01 00:00:00', '2026-01-01 00:00:00', 'CTI', '',
    'NONE', NULL, 'DO_NOTHING', 'FIRST',
    'cti_health_probe_job', '', 'SERIAL_EXECUTION', 60,
    0, 'BEAN', '', 'Phase 0 probe', '2026-01-01 00:00:00', ''
);
