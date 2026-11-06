package com.cti.admin.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthProbeJobTest {

    @Test
    void handlerNameFollowsJobNamingRule() throws Exception {
        Method method = HealthProbeJob.class.getMethod("job");
        XxlJob annotation = method.getAnnotation(XxlJob.class);
        assertTrue(annotation.value().startsWith("cti_"));
        assertTrue(annotation.value().endsWith("_job"));
        assertEquals(JobCatalog.HEALTH_PROBE, annotation.value());
    }
}
