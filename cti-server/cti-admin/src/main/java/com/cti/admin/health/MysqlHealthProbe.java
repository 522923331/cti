package com.cti.admin.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MysqlHealthProbe implements HealthProbe {

    private static final Logger log = LoggerFactory.getLogger(MysqlHealthProbe.class);

    private final JdbcTemplate jdbcTemplate;

    public MysqlHealthProbe(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public ComponentHealth check() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (one == null || one != 1) {
                return new ComponentHealth(ComponentHealth.DOWN, "SELECT 1 返回异常");
            }
            return new ComponentHealth(ComponentHealth.UP, "SELECT 1 成功");
        } catch (RuntimeException ex) {
            log.error("mysql health check failed", ex);
            return new ComponentHealth(ComponentHealth.DOWN, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }
}
