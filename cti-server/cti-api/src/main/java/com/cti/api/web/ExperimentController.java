package com.cti.api.web;

import com.cti.strategy.research.ExperimentService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/strategies")
class ExperimentController {

    private final ExperimentService experiments;

    ExperimentController(ExperimentService experiments) {
        this.experiments = experiments;
    }

    @PostMapping("/experiments")
    public JsonNode experiment(@RequestBody JsonNode body) {
        return call(() -> experiments.experiment(body));
    }

    @PostMapping("/health")
    public JsonNode health(@RequestBody JsonNode body) {
        return call(() -> experiments.health(body));
    }

    private JsonNode call(Action action) {
        try {
            return action.run();
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        }
    }

    private interface Action {
        JsonNode run();
    }
}

@Controller
class ExperimentPageController {

    private final ExperimentService experiments;

    ExperimentPageController(ExperimentService experiments) {
        this.experiments = experiments;
    }

    @GetMapping("/experiments")
    public String page(Model model) {
        model.addAttribute("rows", experiments.latest());
        return "experiments";
    }
}
