package com.cti.data.parquet;

import com.cti.common.Versions;
import com.cti.market.model.KlineBar;

import java.util.List;

public interface KlineArchive {

    void write(String symbol, String interval, List<KlineBar> bars);
}
