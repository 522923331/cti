package com.cti.common.job;

public interface ManualJob {

    String name();

    JobTriggerResult run();
}
