package com.cti.strategy;

import java.util.List;

public record RegimeBatch(String strategyVersion, String featureVersion, List<RegimeRow> rows) {
}
