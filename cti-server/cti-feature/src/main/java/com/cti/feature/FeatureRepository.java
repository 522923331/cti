package com.cti.feature;

import java.util.List;

public interface FeatureRepository {

    void saveAll(List<FeatureRow> rows);

    List<FeatureRow> latest(String symbol, String featureVersion, int limit);
}
