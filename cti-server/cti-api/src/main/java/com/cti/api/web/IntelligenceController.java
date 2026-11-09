package com.cti.api.web;

import com.cti.data.intelligence.DerivativesMetricRepository;
import com.cti.data.intelligence.DerivativesMetricRow;
import com.cti.data.intelligence.MarketSnapshotRepository;
import com.cti.data.intelligence.MarketSnapshotRow;
import com.cti.data.intelligence.NewsEventQuery;
import com.cti.data.intelligence.NewsEventRow;
import com.cti.data.intelligence.OnchainStatus;
import com.cti.data.intelligence.OnchainSyncService;
import com.cti.strategy.intelligence.JdbcIntelligenceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/intelligence")
public class IntelligenceController {

    private final MarketSnapshotRepository snapshots;
    private final DerivativesMetricRepository metrics;
    private final NewsEventQuery news;
    private final OnchainSyncService onchain;
    private final JdbcIntelligenceRepository intelligence;
    private final ObjectMapper mapper;

    public IntelligenceController(MarketSnapshotRepository snapshots, DerivativesMetricRepository metrics,
                                  NewsEventQuery news, OnchainSyncService onchain,
                                  JdbcIntelligenceRepository intelligence, ObjectMapper mapper) {
        this.snapshots = snapshots;
        this.metrics = metrics;
        this.news = news;
        this.onchain = onchain;
        this.intelligence = intelligence;
        this.mapper = mapper;
    }

    @GetMapping("/market-overview")
    public List<MarketSnapshotRow> marketOverview() {
        return snapshots.latest();
    }

    @GetMapping("/derivatives")
    public List<DerivativesMetricRow> derivatives(@RequestParam String symbol,
                                                  @RequestParam(defaultValue = "20") int limit) {
        int bounded = Math.min(Math.max(limit, 1), 200);
        return metrics.latest(symbol.trim().toUpperCase(), bounded);
    }

    @GetMapping("/news")
    public List<NewsEventRow> news(@RequestParam(required = false) String asset,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        int boundedPage = Math.max(page, 1);
        int boundedSize = Math.min(Math.max(size, 1), 50);
        String code = asset == null || asset.isBlank() ? null : asset.trim().toUpperCase();
        return news.latest(code, (boundedPage - 1) * boundedSize, boundedSize);
    }

    @GetMapping("/news/{eventId}")
    public NewsEventRow newsEvent(@PathVariable String eventId) {
        return news.find(eventId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这条公告"));
    }

    @GetMapping("/onchain")
    public OnchainStatus onchain() {
        return onchain.status();
    }

    @GetMapping("/alerts")
    public List<JdbcIntelligenceRepository.StoredAlert> alerts() {
        return intelligence.latestAlerts();
    }

    @GetMapping("/daily-report")
    public JsonNode dailyReport() throws java.io.IOException {
        String report = intelligence.latestReport()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "还没有情报日报"));
        return mapper.readTree(report);
    }
}
