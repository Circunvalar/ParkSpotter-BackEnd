package com.ucentral.desarrollos.backendparkspotter.shared.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;

/**
 * Estado del servidor para el health check del hosting (Render, Cloud Run...) y para monitores externos
 * que lo consultan cada pocos minutos. Responde 503 si la base de datos no contesta.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public record Health(String status, String database, Instant time) {
    }

    @GetMapping("/api/v1/health")
    public ResponseEntity<Health> health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(new Health("UP", "UP", clock.instant()));
        } catch (RuntimeException e) {
            log.warn("Health check: la base de datos no responde ({})", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new Health("DOWN", "DOWN", clock.instant()));
        }
    }
}
