package com.cti.api.web;

import com.cti.strategy.research.EnsembleService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EnsemblePageController {

    private final EnsembleService ensemble;

    public EnsemblePageController(EnsembleService ensemble) {
        this.ensemble = ensemble;
    }

    @GetMapping("/ensemble")
    public String page(Model model) {
        model.addAttribute("results", ensemble.latest());
        return "ensemble";
    }
}
