package com.cti.admin.health;

public interface HealthProbe {

    String name();

    ComponentHealth check();
}
