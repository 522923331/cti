package com.cti.data.store;

import com.cti.market.model.KlineBar;

import java.util.List;

public interface KlineRepository {

    SaveBatch saveAll(List<KlineBar> bars);

    List<Long> openTimes(String symbol, String interval, long fromOpen, long toOpen);

    List<KlineBar> load(String symbol, String interval, long fromOpen, long toOpen);

    List<KlineBar> pending(String symbol, String interval);

    void markArchived(String symbol, String interval, List<Long> openTimes);

    List<KlineStat> stats();

    List<KlineBar> latest(String symbol, String interval, int limit);
}
