package com.cti.portfolio.query;

import com.cti.common.Versions;
import com.cti.portfolio.AccountRow;
import com.cti.portfolio.EquityRow;
import com.cti.portfolio.PortfolioRepository;
import com.cti.portfolio.PositionRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class PortfolioQueryService {

    private final PortfolioRepository repository;
    private final ObjectMapper objectMapper;

    public PortfolioQueryService(PortfolioRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Optional<AccountView> account() {
        return repository.account(Versions.STRATEGY_NAME, Versions.STRATEGY).map(this::account);
    }

    public Optional<List<PositionView>> positions() {
        return account().map(ignored -> repository.positions(Versions.STRATEGY_NAME, Versions.STRATEGY).stream()
                .map(this::position)
                .toList());
    }

    public Optional<JsonNode> risk() {
        return repository.account(Versions.STRATEGY_NAME, Versions.STRATEGY).map(row -> json(row.riskJson()));
    }

    public Optional<List<EquityView>> equity(int limit) {
        return account().map(ignored -> repository.equity(Versions.STRATEGY_NAME, Versions.STRATEGY, limit).stream()
                .map(this::equity)
                .toList());
    }

    private AccountView account(AccountRow row) {
        return new AccountView(
                row.accountCode(), row.strategyName(), row.strategyVersion(), row.quoteAsset(),
                plain(row.initialCapital()), plain(row.cash()), plain(row.equity()), plain(row.peakEquity()),
                row.asOfDate() == null ? null : row.asOfDate().toString(), json(row.riskJson()));
    }

    private PositionView position(PositionRow row) {
        return new PositionView(
                row.symbol(), plain(row.quantity()), plain(row.averageEntryPrice()), plain(row.stopPrice()),
                plain(row.highestClose()), row.openedOn().toString(), row.updatedOn().toString(), plain(row.marketValue()));
    }

    private EquityView equity(EquityRow row) {
        return new EquityView(
                row.tradeDate().toString(), plain(row.cash()), plain(row.equity()), plain(row.peakEquity()),
                plain(row.drawdown()), plain(row.exposure()));
    }

    private JsonNode json(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("组合风险不是 JSON", ex);
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
