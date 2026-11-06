package com.cti.strategy;

import com.cti.common.Versions;
import com.cti.data.store.AssetRepository;
import com.cti.data.store.AssetRow;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ScoreService {

    private final AssetRepository assets;
    private final ScoreClient client;
    private final RegimeRepository regimes;
    private final ScoreRepository scores;

    public ScoreService(AssetRepository assets, ScoreClient client, RegimeRepository regimes, ScoreRepository scores) {
        this.assets = assets;
        this.client = client;
        this.regimes = regimes;
        this.scores = scores;
    }

    public String buildRegimes() {
        List<String> symbols = symbols();
        if (symbols.isEmpty()) {
            return "没有启用的资产";
        }
        RegimeBatch batch = client.regimes(symbols, null);
        checkVersion(batch.strategyVersion(), batch.featureVersion());
        Set<String> seen = new HashSet<>();
        for (RegimeRow row : batch.rows()) {
            if (!Versions.STRATEGY.equals(row.strategyVersion()) || row.tradeDate() == null || row.regime() == null) {
                throw new IllegalStateException("市场状态行缺少版本或日期");
            }
            if (!seen.add(row.tradeDate().toString())) {
                throw new IllegalStateException("重复的市场状态 " + row.tradeDate());
            }
        }
        regimes.saveAll(batch.rows());
        return "symbols=" + symbols.size() + " rows=" + batch.rows().size() + " version=" + batch.strategyVersion();
    }

    public String buildScores() {
        List<String> symbols = symbols();
        if (symbols.isEmpty()) {
            return "没有启用的资产";
        }
        ScoreBatch batch = client.scores(symbols, null);
        checkVersion(batch.strategyVersion(), batch.featureVersion());
        Set<String> seen = new HashSet<>();
        for (ScoreRow row : batch.rows()) {
            if (!Versions.STRATEGY.equals(row.strategyVersion()) || !Versions.FEATURE.equals(row.featureVersion())) {
                throw new IllegalStateException("评分行版本不一致");
            }
            if (row.sentimentScore() != null) {
                throw new IllegalStateException("情绪分必须保持缺失");
            }
            if (row.symbol() == null || row.tradeDate() == null) {
                throw new IllegalStateException("评分行缺少主键");
            }
            if (!seen.add(row.symbol() + "|" + row.tradeDate())) {
                throw new IllegalStateException("重复评分 " + row.symbol() + " " + row.tradeDate());
            }
        }
        scores.saveAll(batch.rows());
        return "symbols=" + symbols.size() + " rows=" + batch.rows().size() + " version=" + batch.strategyVersion();
    }

    private List<String> symbols() {
        return assets.enabled().stream().map(AssetRow::symbol).toList();
    }

    private static void checkVersion(String strategyVersion, String featureVersion) {
        if (!Versions.STRATEGY.equals(strategyVersion) || !Versions.FEATURE.equals(featureVersion)) {
            throw new IllegalStateException("Python 版本是 " + strategyVersion + " / " + featureVersion);
        }
    }
}
