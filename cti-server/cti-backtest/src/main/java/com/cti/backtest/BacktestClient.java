package com.cti.backtest;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;

public interface BacktestClient {

    JsonNode run(List<String> symbols, String strategy, LocalDate start, LocalDate end, String costScenario);

    JsonNode compare(List<String> symbols, List<String> strategies, LocalDate start, LocalDate end, String costScenario);

    JsonNode walkForward(List<String> symbols, LocalDate start, LocalDate end, String costScenario);

    JsonNode sensitivity(List<String> symbols, LocalDate start, LocalDate end, String costScenario);
}
