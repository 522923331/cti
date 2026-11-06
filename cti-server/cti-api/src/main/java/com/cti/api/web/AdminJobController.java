package com.cti.api.web;

import com.cti.admin.config.AdminProperties;
import com.cti.admin.job.JobBoard;
import com.cti.admin.job.JobCatalog;
import com.cti.admin.job.JobRunRepository;
import com.cti.common.job.JobTriggerResult;
import com.cti.common.job.ManualJob;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/jobs")
public class AdminJobController {

    private final JobRunRepository jobRunRepository;
    private final AdminProperties adminProperties;
    private final Map<String, ManualJob> jobs;

    public AdminJobController(JobRunRepository jobRunRepository,
                              AdminProperties adminProperties,
                              List<ManualJob> manualJobs) {
        this.jobRunRepository = jobRunRepository;
        this.adminProperties = adminProperties;
        Map<String, ManualJob> indexed = new LinkedHashMap<>();
        for (ManualJob job : manualJobs) {
            indexed.put(job.name(), job);
        }
        this.jobs = Map.copyOf(indexed);
    }

    @GetMapping
    public JobBoard jobs() {
        return new JobBoard(JobCatalog.registered(), jobRunRepository.recent(20));
    }

    @PostMapping("/{jobName}/trigger")
    public JobTriggerResult trigger(@PathVariable String jobName) {
        if (!adminProperties.isManualTriggerEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "手动触发已关闭");
        }
        ManualJob job = jobs.get(jobName);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这个可手动触发的任务");
        }
        return job.run();
    }
}
