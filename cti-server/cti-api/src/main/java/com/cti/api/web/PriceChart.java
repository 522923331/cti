package com.cti.api.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class PriceChart {

    private PriceChart() {
    }

    static String svg(List<BigDecimal> close, List<BigDecimal> ema20, List<BigDecimal> ema50, List<BigDecimal> ema200) {
        int width = 760;
        int height = 240;
        int pad = 16;
        List<BigDecimal> values = new ArrayList<>();
        collect(values, close);
        collect(values, ema20);
        collect(values, ema50);
        collect(values, ema200);
        if (values.size() < 2 || close.size() < 2) {
            return "";
        }
        BigDecimal min = values.get(0);
        BigDecimal max = values.get(0);
        for (BigDecimal value : values) {
            if (value.compareTo(min) < 0) {
                min = value;
            }
            if (value.compareTo(max) > 0) {
                max = value;
            }
        }
        if (max.compareTo(min) == 0) {
            max = min.add(BigDecimal.ONE);
        }
        StringBuilder svg = new StringBuilder();
        svg.append("<svg class=\"chart\" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\" role=\"img\"><title>收盘价与均线</title>");
        svg.append(path("close", close, width, height, pad, min, max));
        svg.append(path("ema20", ema20, width, height, pad, min, max));
        svg.append(path("ema50", ema50, width, height, pad, min, max));
        svg.append(path("ema200", ema200, width, height, pad, min, max));
        svg.append("</svg>");
        return svg.toString();
    }

    private static void collect(List<BigDecimal> target, List<BigDecimal> source) {
        if (source == null) {
            return;
        }
        for (BigDecimal value : source) {
            if (value != null) {
                target.add(value);
            }
        }
    }

    private static String path(String css, List<BigDecimal> series, int width, int height, int pad,
                               BigDecimal min, BigDecimal max) {
        if (series == null || series.isEmpty()) {
            return "";
        }
        StringBuilder points = new StringBuilder();
        int n = series.size();
        boolean drawing = false;
        StringBuilder paths = new StringBuilder();
        for (int i = 0; i < n; i++) {
            BigDecimal value = series.get(i);
            if (value == null) {
                if (drawing) {
                    paths.append("<polyline class=\"").append(css).append("\" points=\"").append(points).append("\"/>");
                    points.setLength(0);
                    drawing = false;
                }
                continue;
            }
            double x = pad + (n == 1 ? 0 : (width - pad * 2.0) * i / (n - 1));
            double y = pad + (height - pad * 2.0) * (1 - value.subtract(min).doubleValue() / max.subtract(min).doubleValue());
            if (drawing) {
                points.append(' ');
            }
            points.append(trim(x)).append(',').append(trim(y));
            drawing = true;
        }
        if (drawing) {
            paths.append("<polyline class=\"").append(css).append("\" points=\"").append(points).append("\"/>");
        }
        return paths.toString();
    }

    private static String trim(double value) {
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
