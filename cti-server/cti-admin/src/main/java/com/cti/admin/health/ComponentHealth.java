package com.cti.admin.health;

public record ComponentHealth(String status, String detail) {

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";
    public static final String DEGRADED = "DEGRADED";
}
