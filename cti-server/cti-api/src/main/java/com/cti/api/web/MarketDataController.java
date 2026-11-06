package com.cti.api.web;

import com.cti.data.kline.QualityReport;
import com.cti.data.query.AssetSnapshot;
import com.cti.data.query.KlinePoint;
import com.cti.data.query.MarketQueryService;
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
public class MarketDataController {

    private final MarketQueryService marketQueryService;

    public MarketDataController(MarketQueryService marketQueryService) {
        this.marketQueryService = marketQueryService;
    }

    @GetMapping("/assets")
    public List<AssetSnapshot> assets() {
        return marketQueryService.assets();
    }

    @GetMapping("/assets/{symbol}")
    public AssetSnapshot asset(@PathVariable String symbol) {
        return marketQueryService.asset(symbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这个资产"));
    }

    @GetMapping("/assets/{symbol}/klines")
    public List<KlinePoint> klines(@PathVariable String symbol,
                                   @RequestParam String interval,
                                   @RequestParam(defaultValue = "200") int limit) {
        if (limit < 1 || limit > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit 必须在 1 到 1000 之间");
        }
        AssetSnapshot asset = marketQueryService.asset(symbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这个资产"));
        try {
            return marketQueryService.klines(asset.symbol(), interval, limit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/admin/data-quality")
    public List<QualityReport> dataQuality() {
        return marketQueryService.quality();
    }
}
