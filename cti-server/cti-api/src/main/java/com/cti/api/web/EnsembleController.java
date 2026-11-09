package com.cti.api.web;

import com.cti.strategy.research.EnsembleService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/strategies/ensemble")
public class EnsembleController {

    private final EnsembleService ensemble;

    public EnsembleController(EnsembleService ensemble) {
        this.ensemble = ensemble;
    }

    @GetMapping
    public List<Map<String, Object>> latest() {
        return ensemble.latest();
    }

    @PostMapping
    public JsonNode evaluate(@RequestBody JsonNode body) {
        try {
            return ensemble.evaluate(body);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        }
    }
}
