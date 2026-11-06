package com.cti.admin.health;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemHealthServiceTest {

    @Test
    void marksDownWhenAnyRequiredComponentIsDown() {
        SystemHealth health = service(
                probe("mysql", ComponentHealth.DOWN),
                probe("redis", ComponentHealth.UP),
                probe("xxlJob", ComponentHealth.DEGRADED)).check();

        assertEquals(ComponentHealth.DOWN, health.status());
        assertEquals(3, health.components().size());
    }

    @Test
    void marksDegradedWhenNothingIsDown() {
        SystemHealth health = service(
                probe("mysql", ComponentHealth.UP),
                probe("redis", ComponentHealth.UP),
                probe("xxlJob", ComponentHealth.DEGRADED)).check();

        assertEquals(ComponentHealth.DEGRADED, health.status());
    }

    @Test
    void marksUpWhenEveryComponentIsUp() {
        SystemHealth health = service(
                probe("mysql", ComponentHealth.UP),
                probe("redis", ComponentHealth.UP),
                probe("xxlJob", ComponentHealth.UP)).check();

        assertEquals(ComponentHealth.UP, health.status());
    }

    private static SystemHealthService service(HealthProbe... probes) {
        return new SystemHealthService(List.of(probes));
    }

    private static HealthProbe probe(String name, String status) {
        return new HealthProbe() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public ComponentHealth check() {
                return new ComponentHealth(status, name);
            }
        };
    }
}
