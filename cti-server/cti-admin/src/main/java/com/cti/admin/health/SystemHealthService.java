package com.cti.admin.health;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemHealthService {

    private final List<HealthProbe> probes;

    public SystemHealthService(List<HealthProbe> probes) {
        this.probes = List.copyOf(probes);
    }

    public SystemHealth check() {
        Map<String, ComponentHealth> components = new LinkedHashMap<>();
        boolean down = false;
        boolean degraded = false;
        for (HealthProbe probe : probes) {
            ComponentHealth component = probe.check();
            components.put(probe.name(), component);
            if (ComponentHealth.DOWN.equals(component.status())) {
                down = true;
            } else if (ComponentHealth.DEGRADED.equals(component.status())) {
                degraded = true;
            }
        }
        String status = down ? ComponentHealth.DOWN : degraded ? ComponentHealth.DEGRADED : ComponentHealth.UP;
        return new SystemHealth(status, components, Instant.now());
    }
}
