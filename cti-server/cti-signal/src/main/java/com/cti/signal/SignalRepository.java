package com.cti.signal;

import java.util.List;
import java.util.Optional;

public interface SignalRepository {

    void replaceAll(String strategyVersion, List<SignalRow> rows);

    List<SignalRow> latest(String strategyVersion);

    List<SignalRow> history(String strategyVersion, String symbol, int limit);

    List<SignalRow> actionable(String strategyVersion, String symbol, int limit);

    Optional<SignalRow> find(String signalId);
}
