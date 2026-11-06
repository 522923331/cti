package com.cti.strategy;

import java.util.List;

public interface RegimeRepository {

    void saveAll(List<RegimeRow> rows);

    List<RegimeRow> latest(String strategyVersion, int limit);
}
