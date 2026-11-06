package com.cti.portfolio;

import com.cti.common.Versions;
import com.cti.data.store.AssetRepository;
import com.cti.data.store.AssetRow;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioService {

    private final AssetRepository assets;
    private final PortfolioClient client;
    private final PortfolioRepository repository;

    public PortfolioService(AssetRepository assets, PortfolioClient client, PortfolioRepository repository) {
        this.assets = assets;
        this.client = client;
        this.repository = repository;
    }

    public String refreshRisk() {
        if (repository.account(Versions.STRATEGY_NAME, Versions.STRATEGY).isEmpty()) {
            return "还没有研究账户";
        }
        List<String> symbols = assets.enabled().stream().map(AssetRow::symbol).toList();
        if (!symbols.contains("BTCUSDT")) {
            throw new IllegalStateException("组合风险需要 BTCUSDT");
        }
        String risk = client.risk(symbols, null);
        repository.updateRisk(Versions.STRATEGY_NAME, Versions.STRATEGY, risk);
        return "symbols=" + symbols.size() + " version=" + Versions.STRATEGY;
    }
}
