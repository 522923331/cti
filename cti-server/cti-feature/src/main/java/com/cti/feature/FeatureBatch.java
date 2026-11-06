package com.cti.feature;

import java.util.List;

public record FeatureBatch(String featureVersion, List<FeatureRow> rows) {
}
