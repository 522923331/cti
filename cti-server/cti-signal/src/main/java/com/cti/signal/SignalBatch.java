package com.cti.signal;

import com.cti.portfolio.AccountRow;
import com.cti.portfolio.EquityRow;
import com.cti.portfolio.PositionRow;

import java.util.List;

public record SignalBatch(
        String strategyVersion,
        String featureVersion,
        List<SignalRow> signals,
        AccountRow account,
        List<PositionRow> positions,
        List<EquityRow> equity
) {
}
