package com.cti.admin.health;

import com.cti.admin.config.XxlJobProperties;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class XxlJobHealthProbe implements HealthProbe {

    private static final Logger log = LoggerFactory.getLogger(XxlJobHealthProbe.class);

    private final XxlJobProperties properties;
    private final ObjectProvider<XxlJobSpringExecutor> executor;
    private final HttpClient httpClient;

    public XxlJobHealthProbe(XxlJobProperties properties, ObjectProvider<XxlJobSpringExecutor> executor) {
        this.properties = properties;
        this.executor = executor;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Override
    public String name() {
        return "xxlJob";
    }

    @Override
    public ComponentHealth check() {
        if (!properties.isEnabled()) {
            return new ComponentHealth(ComponentHealth.DEGRADED, "执行器配置为关闭");
        }
        if (executor.getIfAvailable() == null) {
            return new ComponentHealth(ComponentHealth.DOWN, "执行器没有启动");
        }
        String admin = properties.getAdminAddresses();
        if (adminReachable(admin)) {
            return new ComponentHealth(ComponentHealth.UP, "执行器已启动，Admin 可达：" + admin);
        }
        return new ComponentHealth(
                ComponentHealth.DEGRADED,
                "执行器已在本进程启动，Admin 当前不可达：" + admin + "。本地可以用探测任务接口验证执行器。");
    }

    private boolean adminReachable(String adminAddresses) {
        if (adminAddresses == null || adminAddresses.isBlank()) {
            return false;
        }
        String first = adminAddresses.split(",")[0].trim();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(first))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 500;
        } catch (Exception ex) {
            log.warn("xxl-job admin unreachable: {}", ex.toString());
            return false;
        }
    }
}
