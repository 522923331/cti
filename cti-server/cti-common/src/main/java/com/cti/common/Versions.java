package com.cti.common;

/**
 * Versions that every later calculation must stamp onto its output.
 * Feature and strategy versions stay distinct: a formula change bumps one of them,
 * and historical rows are never overwritten in place.
 */
public final class Versions {

    public static final String FEATURE = "features-1.0.0";
    public static final String STRATEGY_NAME = "CTI_TREND_V1";
    public static final String STRATEGY = "1.0.0";

    private Versions() {
    }
}
