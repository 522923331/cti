package com.cti.api.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;

@Component("fmt")
public class ResearchFormat {

    private final ObjectMapper objectMapper;

    public ResearchFormat(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String pct(String value) {
        BigDecimal number = decimal(value);
        if (number == null) {
            return "—";
        }
        return number.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    public String num(String value) {
        BigDecimal number = decimal(value);
        if (number == null) {
            return "—";
        }
        return number.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public String money(String value) {
        BigDecimal number = decimal(value);
        if (number == null) {
            return "—";
        }
        return number.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public String text(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    public String ratio(String part, String whole) {
        BigDecimal left = decimal(part);
        BigDecimal right = decimal(whole);
        if (left == null || right == null || right.signum() == 0) {
            return "—";
        }
        return pct(left.divide(right, 8, RoundingMode.HALF_UP).toPlainString());
    }

    public String day(long openTime) {
        return Instant.ofEpochMilli(openTime).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    public String clock(long openTime) {
        return Instant.ofEpochMilli(openTime).atZone(ZoneOffset.UTC).toLocalTime().toString();
    }

    public String json(Object value) {
        if (value == null) {
            return "—";
        }
        try {
            Object parsed = value instanceof String text ? objectMapper.readTree(text) : value;
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (JsonProcessingException ex) {
            return value.toString();
        }
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        return new BigDecimal(value);
    }
}
