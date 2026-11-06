package com.cti.signal;

import com.cti.common.Versions;
import com.cti.data.store.AssetRepository;
import com.cti.data.store.AssetRow;
import com.cti.portfolio.AccountRow;
import com.cti.portfolio.EquityRow;
import com.cti.portfolio.PortfolioRepository;
import com.cti.portfolio.PositionRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignalServiceTest {

    @Test
    void savesOneBuyAndRejectsASecondCopy() {
        List<SignalRow> saved = new ArrayList<>();
        List<AccountRow> accounts = new ArrayList<>();
        SignalRow buy = row("1.0.0:BTCUSDT:2026-11-05:BUY:CTI_TREND_V1", "BUY", "WARNING");
        SignalService service = service(List.of(buy), saved, accounts);
        String message = service.generate();
        assertEquals(1, saved.size());
        assertEquals(1, accounts.size());
        assertTrue(message.contains("buys=1"));

        SignalService duplicate = service(List.of(buy, buy), saved, accounts);
        assertThrows(IllegalStateException.class, duplicate::generate);
        assertEquals(1, saved.size());
    }

    @Test
    void rejectsAnInvalidBuy() {
        List<SignalRow> saved = new ArrayList<>();
        SignalService service = service(List.of(row("1.0.0:BTCUSDT:2026-11-05:BUY:CTI_TREND_V1", "BUY", "INVALID")), saved, new ArrayList<>());
        assertThrows(IllegalStateException.class, service::generate);
        assertTrue(saved.isEmpty());
    }

    private static SignalService service(List<SignalRow> rows, List<SignalRow> saved, List<AccountRow> accounts) {
        AssetRepository assets = new AssetRepository() {
            @Override
            public List<AssetRow> enabled() {
                return List.of(new AssetRow("BTC", "Bitcoin", "BTCUSDT", "SPOT", true));
            }

            @Override
            public Optional<AssetRow> find(String symbolOrCode) {
                return Optional.empty();
            }
        };
        SignalClient client = (symbols, tradeDate) -> new SignalBatch(
                Versions.STRATEGY, Versions.FEATURE, rows, account(), List.of(), List.of());
        SignalRepository signals = new SignalRepository() {
            @Override
            public void replaceAll(String strategyVersion, List<SignalRow> batch) {
                saved.clear();
                saved.addAll(batch);
            }

            @Override
            public List<SignalRow> latest(String strategyVersion) {
                return List.of();
            }

            @Override
            public List<SignalRow> history(String strategyVersion, String symbol, int limit) {
                return List.of();
            }

            @Override
            public List<SignalRow> actionable(String strategyVersion, String symbol, int limit) {
                return List.of();
            }

            @Override
            public Optional<SignalRow> find(String signalId) {
                return Optional.empty();
            }
        };
        PortfolioRepository portfolio = new PortfolioRepository() {
            @Override
            public void replace(AccountRow account, List<PositionRow> positions, List<EquityRow> equity) {
                accounts.add(account);
            }

            @Override
            public void updateRisk(String accountCode, String strategyVersion, String riskJson) {
            }

            @Override
            public Optional<AccountRow> account(String accountCode, String strategyVersion) {
                return Optional.empty();
            }

            @Override
            public List<PositionRow> positions(String accountCode, String strategyVersion) {
                return List.of();
            }

            @Override
            public List<EquityRow> equity(String accountCode, String strategyVersion, int limit) {
                return List.of();
            }
        };
        return new SignalService(assets, client, signals, portfolio);
    }

    private static AccountRow account() {
        return new AccountRow(Versions.STRATEGY_NAME, Versions.STRATEGY_NAME, Versions.STRATEGY, "USDT",
                new BigDecimal("100000"), new BigDecimal("100000"), new BigDecimal("100000"),
                new BigDecimal("100000"), LocalDate.parse("2026-11-05"), "{}");
    }

    private static SignalRow row(String id, String type, String status) {
        return new SignalRow(id, "BTCUSDT", LocalDateTime.parse("2026-11-05T23:59:59"), LocalDate.parse("2026-11-05"),
                type, status, new BigDecimal("70"), new BigDecimal("100"), new BigDecimal("90"),
                new BigDecimal("0.10"), new BigDecimal("500"), "{}", null, Versions.FEATURE, Versions.STRATEGY);
    }
}
