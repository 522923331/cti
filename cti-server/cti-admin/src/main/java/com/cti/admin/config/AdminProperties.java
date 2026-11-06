package com.cti.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cti.admin")
public class AdminProperties {

    /**
     * Local verification hook. XXL-Job Admin remains the scheduler;
     * this flag only allows invoking the Phase 0 probe by hand.
     */
    private boolean manualTriggerEnabled = true;

    public boolean isManualTriggerEnabled() {
        return manualTriggerEnabled;
    }

    public void setManualTriggerEnabled(boolean manualTriggerEnabled) {
        this.manualTriggerEnabled = manualTriggerEnabled;
    }
}
