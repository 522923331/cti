package com.cti.api.web;

import com.cti.admin.health.ComponentHealth;
import com.cti.admin.health.SystemHealth;
import com.cti.admin.health.SystemHealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSystemController {

    private final SystemHealthService systemHealthService;

    public AdminSystemController(SystemHealthService systemHealthService) {
        this.systemHealthService = systemHealthService;
    }

    @GetMapping("/system-health")
    public ResponseEntity<SystemHealth> systemHealth() {
        SystemHealth health = systemHealthService.check();
        HttpStatus status = ComponentHealth.DOWN.equals(health.status())
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(health);
    }
}
