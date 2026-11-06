package com.cti.strategy.query;

import java.util.List;

public record MarketOverview(RegimeView regime, List<ScoreView> ranking) {
}
