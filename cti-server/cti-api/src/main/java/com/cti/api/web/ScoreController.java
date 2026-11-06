package com.cti.api.web;

import com.cti.strategy.query.BreadthPoint;
import com.cti.strategy.query.MarketOverview;
import com.cti.strategy.query.RegimeView;
import com.cti.strategy.query.ScoreQueryService;
import com.cti.strategy.query.ScoreView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ScoreController {

    private final ScoreQueryService scores;

    public ScoreController(ScoreQueryService scores) {
        this.scores = scores;
    }

    @GetMapping("/market/overview")
    public MarketOverview overview() {
        return scores.overview();
    }

    @GetMapping("/market/regime")
    public List<RegimeView> regime(@RequestParam(defaultValue = "200") int limit) {
        return scores.regimes(bounded(limit));
    }

    @GetMapping("/market/breadth")
    public List<BreadthPoint> breadth(@RequestParam(defaultValue = "200") int limit) {
        return scores.breadth(bounded(limit));
    }

    @GetMapping("/trend/ranking")
    public List<ScoreView> ranking(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        return scores.ranking(tradeDate);
    }

    @GetMapping("/trend/{symbol}")
    public ScoreView latest(@PathVariable String symbol) {
        if (scores.history(symbol, 1).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这个资产");
        }
        return scores.latest(symbol).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "还没有评分"));
    }

    @GetMapping("/trend/{symbol}/history")
    public List<ScoreView> history(@PathVariable String symbol, @RequestParam(defaultValue = "200") int limit) {
        return scores.history(symbol, bounded(limit))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这个资产"));
    }

    private static int bounded(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit 必须在 1 到 1000 之间");
        }
        return limit;
    }
}
