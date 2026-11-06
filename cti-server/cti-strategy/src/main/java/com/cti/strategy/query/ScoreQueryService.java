package com.cti.strategy.query;

import com.cti.common.Versions;
import com.cti.data.store.AssetRepository;
import com.cti.strategy.RegimeRepository;
import com.cti.strategy.RegimeRow;
import com.cti.strategy.ScoreRepository;
import com.cti.strategy.ScoreRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ScoreQueryService {

    private final AssetRepository assets;
    private final RegimeRepository regimes;
    private final ScoreRepository scores;
    private final ObjectMapper objectMapper;

    public ScoreQueryService(AssetRepository assets, RegimeRepository regimes, ScoreRepository scores, ObjectMapper objectMapper) {
        this.assets = assets;
        this.regimes = regimes;
        this.scores = scores;
        this.objectMapper = objectMapper;
    }

    public MarketOverview overview() {
        List<RegimeRow> latest = regimes.latest(Versions.STRATEGY, 1);
        RegimeView regime = latest.isEmpty() ? null : regime(latest.get(0));
        LocalDate day = regime == null ? scores.latestDate(Versions.STRATEGY) : LocalDate.parse(regime.tradeDate());
        return new MarketOverview(regime, day == null ? List.of() : ranked(scores.ranking(day, Versions.STRATEGY)));
    }

    public List<RegimeView> regimes(int limit) {
        return regimes.latest(Versions.STRATEGY, limit).stream().map(this::regime).toList();
    }

    public List<BreadthPoint> breadth(int limit) {
        return regimes.latest(Versions.STRATEGY, limit).stream()
                .map(row -> new BreadthPoint(row.tradeDate().toString(), plain(row.marketBreadth())))
                .toList();
    }

    public List<ScoreView> ranking(LocalDate tradeDate) {
        LocalDate day = tradeDate == null ? scores.latestDate(Versions.STRATEGY) : tradeDate;
        if (day == null) {
            return List.of();
        }
        return ranked(scores.ranking(day, Versions.STRATEGY));
    }

    public Optional<ScoreView> latest(String symbolOrCode) {
        return assets.find(symbolOrCode).flatMap(asset -> {
            List<ScoreRow> rows = scores.history(asset.symbol(), Versions.STRATEGY, 1);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(score(rows.get(0), null));
        });
    }

    public Optional<List<ScoreView>> history(String symbolOrCode, int limit) {
        return assets.find(symbolOrCode).map(asset -> scores.history(asset.symbol(), Versions.STRATEGY, limit).stream()
                .map(row -> score(row, null))
                .toList());
    }

    private List<ScoreView> ranked(List<ScoreRow> rows) {
        List<ScoreView> views = new ArrayList<>();
        int rank = 1;
        for (ScoreRow row : rows) {
            Integer current = row.totalScore() == null ? null : rank++;
            views.add(score(row, current));
        }
        return views;
    }

    private RegimeView regime(RegimeRow row) {
        return new RegimeView(
                row.tradeDate().toString(), row.regime(), row.rawRegime(), row.confirmCount(),
                plain(row.marketScore()), plain(row.btcTrendScore()), plain(row.marketBreadth()),
                plain(row.btcVolatility()), plain(row.totalExposureLimit()), row.riskOverride(),
                json(row.reasonJson()), row.strategyVersion());
    }

    private ScoreView score(ScoreRow row, Integer rank) {
        return new ScoreView(
                row.symbol(), row.tradeDate().toString(), plain(row.marketScore()), plain(row.trendScore()),
                plain(row.flowScore()), plain(row.derivativesScore()), plain(row.sentimentScore()),
                plain(row.totalScore()), row.signalLevel(), rank, json(row.reasonJson()), json(row.riskJson()),
                row.featureVersion(), row.strategyVersion());
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (Exception ex) {
            throw new IllegalStateException("评分原因不是 JSON", ex);
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
