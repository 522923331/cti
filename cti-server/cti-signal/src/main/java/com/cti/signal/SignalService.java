package com.cti.signal;

import com.cti.common.Versions;
import com.cti.data.store.AssetRepository;
import com.cti.data.store.AssetRow;
import com.cti.portfolio.PortfolioRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SignalService {

    private static final Set<String> TYPES = Set.of("WATCH", "BUY", "HOLD", "REDUCE", "SELL", "NO_TRADE");
    private static final Set<String> STATUSES = Set.of("VALID", "WARNING", "INVALID");

    private final AssetRepository assets;
    private final SignalClient client;
    private final SignalRepository signals;
    private final PortfolioRepository portfolio;

    public SignalService(AssetRepository assets, SignalClient client, SignalRepository signals, PortfolioRepository portfolio) {
        this.assets = assets;
        this.client = client;
        this.signals = signals;
        this.portfolio = portfolio;
    }

    public String generate() {
        List<String> symbols = assets.enabled().stream().map(AssetRow::symbol).toList();
        if (symbols.isEmpty()) {
            return "没有启用的资产";
        }
        if (!symbols.contains("BTCUSDT")) {
            throw new IllegalStateException("信号计算需要 BTCUSDT");
        }
        SignalBatch batch = client.generate(symbols, null);
        if (!Versions.STRATEGY.equals(batch.strategyVersion()) || !Versions.FEATURE.equals(batch.featureVersion())) {
            throw new IllegalStateException("Python 版本是 " + batch.strategyVersion() + " / " + batch.featureVersion());
        }
        if (batch.account() == null || !Versions.STRATEGY_NAME.equals(batch.account().accountCode())) {
            throw new IllegalStateException("研究账户标识不一致");
        }
        Set<String> seen = new HashSet<>();
        for (SignalRow row : batch.signals()) {
            if (!Versions.STRATEGY.equals(row.strategyVersion()) || !Versions.FEATURE.equals(row.featureVersion())) {
                throw new IllegalStateException("信号行版本不一致");
            }
            if (row.signalId() == null || row.signalId().length() > 64 || row.symbol() == null || row.tradeDate() == null) {
                throw new IllegalStateException("信号主键不完整");
            }
            if (!TYPES.contains(row.signalType()) || !STATUSES.contains(row.signalStatus())) {
                throw new IllegalStateException("无法识别的信号 " + row.signalType() + " / " + row.signalStatus());
            }
            if ("BUY".equals(row.signalType()) && (row.stopPrice() == null || row.suggestedPosition() == null)) {
                throw new IllegalStateException("买入信号缺少止损或仓位");
            }
            if ("INVALID".equals(row.signalStatus()) && "BUY".equals(row.signalType())) {
                throw new IllegalStateException("无效数据不能产生买入");
            }
            if (!seen.add(row.signalId())) {
                throw new IllegalStateException("重复信号 " + row.signalId());
            }
        }
        signals.replaceAll(Versions.STRATEGY, batch.signals());
        portfolio.replace(batch.account(), batch.positions(), batch.equity());
        long buys = batch.signals().stream().filter(row -> "BUY".equals(row.signalType())).count();
        return "symbols=" + symbols.size() + " rows=" + batch.signals().size() + " buys=" + buys
                + " positions=" + batch.positions().size() + " version=" + batch.strategyVersion();
    }
}
