package com.cti.api.web;

import com.cti.strategy.research.ResearchStrategyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ResearchStrategyPageController {

    private final ResearchStrategyService strategies;

    public ResearchStrategyPageController(ResearchStrategyService strategies) {
        this.strategies = strategies;
    }

    @GetMapping("/research-strategies")
    public String page(Model model) {
        model.addAttribute("strategies", strategies.registry());
        return "research-strategies";
    }
}
