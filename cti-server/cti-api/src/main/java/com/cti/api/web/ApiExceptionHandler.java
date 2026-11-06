package com.cti.api.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        log.warn("request rejected: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(failed(ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception ex) {
        log.error("request failed", ex);
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(failed(message));
    }

    private static Map<String, Object> failed(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "FAILED");
        body.put("error", message);
        body.put("traceId", MDC.get(TraceIdFilter.TRACE_ID));
        return body;
    }
}
