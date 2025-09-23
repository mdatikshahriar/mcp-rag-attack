package com.example.server.tools;

import com.example.server.service.UpdateSignalService;
import io.modelcontextprotocol.server.McpSyncServer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@AllArgsConstructor
public class McpCommandLineRunner implements CommandLineRunner {
    private static final long STARTUP_TIMEOUT_SECONDS = 30;

    private final McpSyncServer mcpSyncServer;
    private final UpdateSignalService updateSignalService;

    @Override
    public void run(String... args) throws Exception {
        log.info("=== Generic Academic MCP Command Line Runner Starting ===");

        try {
            if (mcpSyncServer != null && mcpSyncServer.getServerInfo() != null) {
                log.info("Generic Academic MCP Server Info: {}", mcpSyncServer.getServerInfo());
            } else {
                log.warn("Generic Academic MCP Server or ServerInfo is null");
            }

            if (args.length > 0) {
                log.info("Command line arguments provided: {}", String.join(", ", args));
            }

            log.info("Waiting for update signal to complete server initialization...");
            LocalDateTime waitStartTime = LocalDateTime.now();

            boolean signalReceived =
                    updateSignalService.awaitUpdate(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (signalReceived) {
                LocalDateTime completionTime = LocalDateTime.now();
                long waitDurationSeconds =
                        java.time.Duration.between(waitStartTime, completionTime).getSeconds();
                log.info("✅ Update signal received successfully");
                log.info("Generic Academic server initialization completed at: {}", completionTime);
                log.debug("Initialization wait duration: {} seconds", waitDurationSeconds);
                logServerStatus();
            } else {
                LocalDateTime timeoutTime = LocalDateTime.now();
                long totalWaitSeconds =
                        java.time.Duration.between(waitStartTime, timeoutTime).getSeconds();
                log.error(
                        "❌ Timeout waiting for update signal after {} seconds (actual wait: {} seconds)",
                        STARTUP_TIMEOUT_SECONDS, totalWaitSeconds);
                logServerStatus();
                log.warn("Continuing with potentially incomplete initialization");
            }

        } catch (InterruptedException e) {
            log.error("Generic Academic MCP Command Line Runner was interrupted", e);
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in Generic Academic MCP Command Line Runner", e);
            throw e;
        }

        log.info("=== Generic Academic MCP Command Line Runner Completed ===");
    }

    private void logServerStatus() {
        try {
            log.info("📊 Final Generic Academic Server Status:");
            log.info("   - Update Signal Sent: {}", updateSignalService.isSignalSent());
            log.info("   - Total Signals: {}", updateSignalService.getSignalCount());
            log.info("   - Last Signal Time: {}", updateSignalService.getLastSignalTime());

        } catch (Exception e) {
            log.error("Error logging server status", e);
        }
    }
}
