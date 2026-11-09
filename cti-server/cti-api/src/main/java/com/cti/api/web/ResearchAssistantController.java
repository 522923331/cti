package com.cti.api.web;

import com.cti.strategy.research.ResearchAssistantService;
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
@RequestMapping("/api/v1/research/assistant")
class ResearchAssistantController {

    private final ResearchAssistantService assistant;

    ResearchAssistantController(ResearchAssistantService assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/query")
    public JsonNode query(@RequestBody JsonNode body) {
        try {
            return assistant.ask(body);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        }
    }
}

@Controller
class ResearchAssistantPageController {

    private final ResearchAssistantService assistant;

    ResearchAssistantPageController(ResearchAssistantService assistant) {
        this.assistant = assistant;
    }

    @GetMapping("/assistant")
    public String page(Model model) {
        model.addAttribute("rows", assistant.latest());
        return "assistant";
    }
}
