package com.cti.data.kline;

import com.cti.market.model.KlineBar;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record QualityReport(
        String symbol,
        String intervalCode,
        LocalDate checkDate,
        String status,
        int missingCount,
        int duplicateCount,
        int unclosedCount,
        int anomalyCount,
        String detailJson
) {

    public static final String NORMAL = "NORMAL";
    public static final String WARNING = "WARNING";
    public static final String INVALID = "INVALID";

    public static QualityReport check(String symbol, String intervalCode, List<KlineBar> bars,
                                       long startOpen, long lastClosedOpen, long step,
                                       String exchangeStatus, LocalDate checkDate) {
        long formingOpen = lastClosedOpen + step;
        int anomalies = 0;
        int unclosed = 0;
        Map<Long, Integer> counts = new HashMap<>();
        for (KlineBar bar : bars) {
            counts.merge(bar.openTime(), 1, Integer::sum);
            if (!bar.closed()) {
                unclosed++;
            }
            if (anomalous(bar, formingOpen)) {
                anomalies++;
            }
        }
        int duplicates = 0;
        for (int count : counts.values()) {
            if (count > 1) {
                duplicates += count - 1;
            }
        }
        Set<Long> closedOpens = new HashSet<>();
        for (KlineBar bar : bars) {
            if (bar.closed() && bar.openTime() >= startOpen && bar.openTime() <= lastClosedOpen) {
                closedOpens.add(bar.openTime());
            }
        }
        int missing = KlineGapFinder.missing(startOpen, lastClosedOpen, closedOpens, step).size();
        String status;
        if (bars.isEmpty() || missing > 0 || anomalies > 0 || duplicates > 0) {
            status = INVALID;
        } else if (exchangeStatus == null || !"TRADING".equals(exchangeStatus)) {
            status = WARNING;
        } else {
            status = NORMAL;
        }
        String exchangeJson = exchangeStatus == null ? "null" : "\"" + exchangeStatus + "\"";
        String detail = "{\"missing\":" + missing
                + ",\"duplicates\":" + duplicates
                + ",\"anomalies\":" + anomalies
                + ",\"unclosed\":" + unclosed
                + ",\"exchangeStatus\":" + exchangeJson + "}";
        return new QualityReport(symbol, intervalCode, checkDate, status, missing, duplicates, unclosed, anomalies, detail);
    }

    public static boolean anomalous(KlineBar bar, long formingOpen) {
        if (bar.high().compareTo(bar.low()) < 0) {
            return true;
        }
        if (bar.open().compareTo(bar.low()) < 0 || bar.open().compareTo(bar.high()) > 0) {
            return true;
        }
        if (bar.close().compareTo(bar.low()) < 0 || bar.close().compareTo(bar.high()) > 0) {
            return true;
        }
        if (bar.open().signum() <= 0 || bar.high().signum() <= 0 || bar.low().signum() <= 0 || bar.close().signum() <= 0) {
            return true;
        }
        if (bar.volume().signum() < 0) {
            return true;
        }
        if (bar.quoteVolume() != null && bar.quoteVolume().signum() < 0) {
            return true;
        }
        return !bar.closed() && bar.openTime() != formingOpen;
    }
}
