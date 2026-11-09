package com.cti.strategy.intelligence;

import java.util.List;

public final class AlertPolicy {

    public boolean allow(boolean enabled, long triggeredAtMs, int cooldownMinutes, List<PriorAlert> prior, String alertId) {
        if (!enabled) {
            return false;
        }
        for (PriorAlert alert : prior) {
            if (alert.alertId().equals(alertId)) {
                return false;
            }
            boolean open = "CREATED".equals(alert.status()) || "ACKNOWLEDGED".equals(alert.status());
            long elapsed = triggeredAtMs - alert.triggeredAtMs();
            if (open && elapsed < cooldownMinutes * 60_000L) {
                return false;
            }
        }
        return true;
    }

    public record PriorAlert(String alertId, String status, long triggeredAtMs) {
    }
}
