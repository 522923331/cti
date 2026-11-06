package com.cti.api.web;

import com.cti.portfolio.query.AccountView;
import com.cti.portfolio.query.EquityView;
import com.cti.portfolio.query.PortfolioQueryService;
import com.cti.portfolio.query.PositionView;
import com.cti.signal.query.SignalQueryService;
import com.cti.signal.query.SignalView;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SignalController {

    private final SignalQueryService signals;
    private final PortfolioQueryService portfolio;

    public SignalController(SignalQueryService signals, PortfolioQueryService portfolio) {
        this.signals = signals;
        this.portfolio = portfolio;
    }

    @GetMapping("/signals/latest")
    public List<SignalView> latest() {
        return signals.latest();
    }

    @GetMapping("/signals/{signalId}")
    public SignalView one(@PathVariable String signalId) {
        return signals.find(signalId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这条信号"));
    }

    @GetMapping("/signals")
    public List<SignalView> history(@RequestParam(required = false) String symbol, @RequestParam(defaultValue = "200") int limit) {
        return signals.history(symbol, bounded(limit))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这个资产"));
    }

    @GetMapping("/portfolio")
    public AccountView account() {
        return portfolio.account().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "还没有研究账户"));
    }

    @GetMapping("/portfolio/positions")
    public List<PositionView> positions() {
        return portfolio.positions().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "还没有研究账户"));
    }

    @GetMapping("/portfolio/risk")
    public JsonNode risk() {
        return portfolio.risk().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "还没有研究账户"));
    }

    @GetMapping("/portfolio/equity")
    public List<EquityView> equity(@RequestParam(defaultValue = "200") int limit) {
        return portfolio.equity(bounded(limit)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "还没有研究账户"));
    }

    private static int bounded(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit 必须在 1 到 1000 之间");
        }
        return limit;
    }
}
