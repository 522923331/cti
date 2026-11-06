package com.cti.api.web;

import com.cti.feature.query.FeatureQueryService;
import com.cti.feature.query.FeatureView;
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
public class FeatureController {

    private final FeatureQueryService featureQueryService;

    public FeatureController(FeatureQueryService featureQueryService) {
        this.featureQueryService = featureQueryService;
    }

    @GetMapping("/assets/{symbol}/features")
    public List<FeatureView> features(@PathVariable String symbol, @RequestParam(defaultValue = "200") int limit) {
        if (limit < 1 || limit > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit 必须在 1 到 1000 之间");
        }
        return featureQueryService.latest(symbol, limit)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这个资产"));
    }
}
