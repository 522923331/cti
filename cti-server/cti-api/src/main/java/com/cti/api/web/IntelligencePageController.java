package com.cti.api.web;

import com.cti.strategy.intelligence.JdbcIntelligenceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;

@Controller
public class IntelligencePageController {

    private final JdbcIntelligenceRepository intelligence;
    private final ObjectMapper mapper;

    public IntelligencePageController(JdbcIntelligenceRepository intelligence, ObjectMapper mapper) {
        this.intelligence = intelligence;
        this.mapper = mapper;
    }

    @GetMapping("/intelligence")
    public String page(Model model) throws java.io.IOException {
        model.addAttribute("scores", intelligence.latestScores());
        model.addAttribute("alerts", intelligence.latestAlerts());
        List<String> conclusions = new ArrayList<>();
        String report = intelligence.latestReport().orElse(null);
        if (report != null) {
            for (JsonNode item : mapper.readTree(report).path("conclusions")) {
                conclusions.add(item.path("text").asText() + " · " + item.path("table").asText() + "." + item.path("field").asText());
            }
        }
        model.addAttribute("conclusions", conclusions);
        return "intelligence";
    }
}
