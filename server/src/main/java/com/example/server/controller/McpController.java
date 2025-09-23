package com.example.server.controller;

import com.example.server.service.UpdateSignalService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@AllArgsConstructor
public class McpController {
    private final UpdateSignalService updateSignalService;

    @GetMapping("/updateTools")
    public ResponseEntity<String> updateTools(HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        log.info("Received update tools request from client IP: {}", clientIp);

        try {
            updateSignalService.signalUpdate();
            log.info("Successfully sent update signal to Generic Academic MCP server");

            return ResponseEntity.ok("Generic Academic MCP update signal sent successfully!");

        } catch (Exception e) {
            log.error("Failed to send update signal from client {}", clientIp, e);
            return ResponseEntity.internalServerError()
                    .body("Failed to send update signal: " + e.getMessage());
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck(HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        log.debug("Health check requested from client IP: {}", clientIp);

        try {
            String status =
                    "Generic Academic MCP Server is running - Calculator, Statistics, and Lookup tools available";
            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Health check failed for client {}", clientIp, e);
            return ResponseEntity.internalServerError()
                    .body("Health check failed: " + e.getMessage());
        }
    }

    @GetMapping("/api/academic/status")
    public ResponseEntity<Map<String, Object>> getAcademicSystemStatus(HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        log.info("Academic system status requested from client IP: {}", clientIp);

        try {
            Map<String, Object> status = new HashMap<>();
            status.put("server_name", "Generic Academic MCP Server");
            status.put("status", "active");
            status.put("available_services",
                    Arrays.asList("gpa_calculator", "statistics", "lookup"));
            status.put("external_apis", Arrays.asList("arxiv", "crossref", "openlibrary"));
            status.put("timestamp", java.time.LocalDateTime.now());
            status.put("version", "2.0.0");

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Failed to compile academic system status for client {}", clientIp, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get system status: " + e.getMessage()));
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
