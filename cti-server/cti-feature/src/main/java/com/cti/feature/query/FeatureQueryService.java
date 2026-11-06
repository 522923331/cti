package com.cti.feature.query;

import com.cti.common.Versions;
import com.cti.data.store.AssetRepository;
import com.cti.feature.FeatureRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FeatureQueryService {

    private final AssetRepository assets;
    private final FeatureRepository features;

    public FeatureQueryService(AssetRepository assets, FeatureRepository features) {
        this.assets = assets;
        this.features = features;
    }

    public Optional<List<FeatureView>> latest(String symbolOrCode, int limit) {
        return assets.find(symbolOrCode).map(asset -> features.latest(asset.symbol(), Versions.FEATURE, limit).stream()
                .map(FeatureView::from)
                .toList());
    }
}
