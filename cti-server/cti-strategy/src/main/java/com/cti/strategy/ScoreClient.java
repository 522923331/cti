package com.cti.strategy;

import java.time.LocalDate;
import java.util.List;

public interface ScoreClient {

    RegimeBatch regimes(List<String> symbols, LocalDate tradeDate);

    ScoreBatch scores(List<String> symbols, LocalDate tradeDate);
}
