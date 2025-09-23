package com.example.server;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@AllArgsConstructor
@SpringBootApplication
public class AcademicMcpServer {
    private final Environment environment;

    public static void main(String[] args) {
        log.info("Starting Academic MCP Server...");
        try {
            SpringApplication.run(AcademicMcpServer.class, args);
            log.info("Academic MCP Server started successfully");
        } catch (Exception e) {
            log.error("Failed to start Academic MCP Server", e);
            System.exit(1);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        String port = environment.getProperty("server.port", "8082");
        String mcpEndpoint = environment.getProperty("spring.ai.mcp.server.sse-message-endpoint",
                "/mcp/message");
        String serverName =
                environment.getProperty("spring.ai.mcp.server.name", "academic-tools-server");

        log.info("=== Academic MCP Server Ready ===");
        log.info("Server Name: {}", serverName);
        log.info("Server Port: {}", port);
        log.info("MCP Endpoint: http://localhost:{}{}", port, mcpEndpoint);
        log.info("Academic Tools: http://localhost:{}/api/academic", port);
        log.info("Update Tools: http://localhost:{}/updateTools", port);
        log.info("Health Check: http://localhost:{}/health", port);
        log.info("========================================");
    }
}
