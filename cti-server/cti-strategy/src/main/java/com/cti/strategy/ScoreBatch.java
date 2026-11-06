package com.cti.strategy;

import java.util.List;

public record ScoreBatch(String strategyVersion, String featureVersion, List<ScoreRow> rows) {
}
