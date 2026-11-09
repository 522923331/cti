package com.cti.api.web;

import com.cti.strategy.research.ResearchStrategyService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/strategies/research")
public class ResearchStrategyController {

    private final ResearchStrategyService strategies;

    public ResearchStrategyController(ResearchStrategyService strategies) {
        this.strategies = strategies;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return strategies.registry();
    }

    @PostMapping("/evaluate")
    public JsonNode evaluate(@RequestBody JsonNode body) {
        return strategies.evaluate(body);
    }
}
