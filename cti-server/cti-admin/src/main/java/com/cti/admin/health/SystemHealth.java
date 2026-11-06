package com.cti.admin.health;

import java.time.Instant;
import java.util.Map;

public record SystemHealth(String status, Map<String, ComponentHealth> components, Instant time) {
}
