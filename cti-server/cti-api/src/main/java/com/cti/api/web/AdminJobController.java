package com.cti.api.web;

import com.cti.admin.config.AdminProperties;
import com.cti.admin.job.HealthProbeJob;
import com.cti.admin.job.JobBoard;
import com.cti.admin.job.JobCatalog;
import com.cti.admin.job.JobRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/jobs")
public class AdminJobController {

    private final JobRunRepository jobRunRepository;
    private final HealthProbeJob healthProbeJob;
    private final AdminProperties adminProperties;

    public AdminJobController(JobRunRepository jobRunRepository,
                              HealthProbeJob healthProbeJob,
                              AdminProperties adminProperties) {
        this.jobRunRepository = jobRunRepository;
        this.healthProbeJob = healthProbeJob;
        this.adminProperties = adminProperties;
    }

    @GetMapping
    public JobBoard jobs() {
        return new JobBoard(JobCatalog.registered(), jobRunRepository.recent(20));
    }

    @PostMapping("/{jobName}/trigger")
    public HealthProbeJob.ProbeResult trigger(@PathVariable String jobName) {
        if (!adminProperties.isManualTriggerEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "手动触发已关闭");
        }
        if (!JobCatalog.HEALTH_PROBE.equals(jobName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这个可手动触发的任务");
        }
        return healthProbeJob.execute();
    }
}
