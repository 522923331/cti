package com.cti.signal.query;

import com.cti.common.Versions;
import com.cti.data.store.AssetRepository;
import com.cti.signal.SignalRepository;
import com.cti.signal.SignalRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class SignalQueryService {

    private final AssetRepository assets;
    private final SignalRepository signals;
    private final ObjectMapper objectMapper;

    public SignalQueryService(AssetRepository assets, SignalRepository signals, ObjectMapper objectMapper) {
        this.assets = assets;
        this.signals = signals;
        this.objectMapper = objectMapper;
    }

    public List<SignalView> latest() {
        return signals.latest(Versions.STRATEGY).stream().map(this::view).toList();
    }

    public Optional<List<SignalView>> history(String symbolOrCode, int limit) {
        if (symbolOrCode == null || symbolOrCode.isBlank()) {
            return Optional.of(signals.history(Versions.STRATEGY, null, limit).stream().map(this::view).toList());
        }
        return assets.find(symbolOrCode).map(asset -> signals.history(Versions.STRATEGY, asset.symbol(), limit).stream()
                .map(this::view)
                .toList());
    }

    public List<SignalView> actionable(String symbolOrCode, int limit) {
        if (symbolOrCode == null || symbolOrCode.isBlank()) {
            return signals.actionable(Versions.STRATEGY, null, limit).stream().map(this::view).toList();
        }
        return assets.find(symbolOrCode)
                .map(asset -> signals.actionable(Versions.STRATEGY, asset.symbol(), limit).stream().map(this::view).toList())
                .orElse(List.of());
    }

    public Optional<SignalView> find(String signalId) {
        return signals.find(signalId).map(this::view);
    }

    private SignalView view(SignalRow row) {
        return new SignalView(
                row.signalId(),
                row.symbol(),
                row.signalTime().toString(),
                row.tradeDate().toString(),
                row.signalType(),
                row.signalStatus(),
                plain(row.score()),
                plain(row.entryPrice()),
                plain(row.stopPrice()),
                plain(row.suggestedPosition()),
                plain(row.riskAmount()),
                json(row.signalReason()),
                json(row.invalidationReason()),
                row.featureVersion(),
                row.strategyVersion());
    }

    private JsonNode json(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("信号原因不是 JSON", ex);
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
