package com.cti.data.kline;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class KlineGapFinder {

    private KlineGapFinder() {
    }

    public static List<Long> missing(long startOpen, long endOpen, Set<Long> actual, long step) {
        if (step <= 0) {
            throw new IllegalArgumentException("K 线步长必须大于 0");
        }
        List<Long> missing = new ArrayList<>();
        if (endOpen < startOpen) {
            return missing;
        }
        for (long open = startOpen; open <= endOpen; open += step) {
            if (!actual.contains(open)) {
                missing.add(open);
            }
        }
        return missing;
    }

    public static List<long[]> ranges(List<Long> missing, long step) {
        List<long[]> ranges = new ArrayList<>();
        if (missing.isEmpty()) {
            return ranges;
        }
        long start = missing.get(0);
        long previous = start;
        for (int i = 1; i < missing.size(); i++) {
            long open = missing.get(i);
            if (open != previous + step) {
                ranges.add(new long[]{start, previous});
                start = open;
            }
            previous = open;
        }
        ranges.add(new long[]{start, previous});
        return ranges;
    }
}
