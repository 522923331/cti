package com.cti.strategy;

import java.time.LocalDate;
import java.util.List;

public interface ScoreRepository {

    void saveAll(List<ScoreRow> rows);

    List<ScoreRow> history(String symbol, String strategyVersion, int limit);

    List<ScoreRow> ranking(LocalDate tradeDate, String strategyVersion);

    LocalDate latestDate(String strategyVersion);
}
