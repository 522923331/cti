package com.cti.feature;

import com.cti.common.Versions;
import com.cti.data.store.AssetRepository;
import com.cti.data.store.AssetRow;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DailyFeatureService {

    private final AssetRepository assets;
    private final QuantFeatureClient client;
    private final FeatureRepository repository;

    public DailyFeatureService(AssetRepository assets, QuantFeatureClient client, FeatureRepository repository) {
        this.assets = assets;
        this.client = client;
        this.repository = repository;
    }

    public String build(java.time.LocalDate tradeDate) {
        List<String> symbols = assets.enabled().stream().map(AssetRow::symbol).toList();
        if (symbols.isEmpty()) {
            return "没有启用的资产";
        }
        FeatureBatch batch = client.calculate(symbols, tradeDate);
        if (!Versions.FEATURE.equals(batch.featureVersion())) {
            throw new IllegalStateException("Python 特征版本是 " + batch.featureVersion() + "，期望 " + Versions.FEATURE);
        }
        Set<String> seen = new HashSet<>();
        for (FeatureRow row : batch.rows()) {
            if (!Versions.FEATURE.equals(row.featureVersion())) {
                throw new IllegalStateException("特征行版本不一致: " + row.featureVersion());
            }
            if (row.symbol() == null || row.tradeDate() == null || row.dataQualityStatus() == null || row.dataQualityStatus().isBlank()) {
                throw new IllegalStateException("特征行缺少主键或质量状态");
            }
            if (!seen.add(row.symbol() + "|" + row.tradeDate())) {
                throw new IllegalStateException("重复特征 " + row.symbol() + " " + row.tradeDate());
            }
        }
        repository.saveAll(batch.rows());
        return "symbols=" + symbols.size() + " rows=" + batch.rows().size() + " version=" + batch.featureVersion();
    }
}
