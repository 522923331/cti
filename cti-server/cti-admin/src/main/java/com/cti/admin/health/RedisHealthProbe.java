package com.cti.admin.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisHealthProbe implements HealthProbe {

    private static final Logger log = LoggerFactory.getLogger(RedisHealthProbe.class);

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthProbe(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public String name() {
        return "redis";
    }

    @Override
    public ComponentHealth check() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            if (pong != null && "PONG".equalsIgnoreCase(pong)) {
                return new ComponentHealth(ComponentHealth.UP, "PING 得到 PONG");
            }
            return new ComponentHealth(ComponentHealth.DOWN, "PING 返回 " + pong);
        } catch (RuntimeException ex) {
            log.error("redis health check failed", ex);
            return new ComponentHealth(ComponentHealth.DOWN, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }
}
