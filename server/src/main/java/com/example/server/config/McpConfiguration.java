package com.example.server.config;

import com.example.server.service.GpaCalculatorService;
import com.example.server.service.LookupService;
import com.example.server.service.StatisticsService;
import com.example.server.service.UpdateSignalService;
import com.example.server.tools.McpCommandLineRunner;
import io.modelcontextprotocol.server.McpSyncServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class McpConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(McpConfiguration.class);

    public McpConfiguration() {
        logger.info("Initializing Generic Academic MCP Configuration");
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ToolCallbackProvider allTools(GpaCalculatorService gpaService,
            StatisticsService statisticsService, LookupService lookupService) {
        logger.info("Creating ToolCallbackProvider with Academic services");

        try {
            ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                    .toolObjects(gpaService, statisticsService, lookupService).build();

            logger.info("Successfully created ToolCallbackProvider with {} tool objects", 3);
            logger.debug(
                    "Tools registered: GpaCalculatorService, StatisticsService, LookupService");

            return provider;
        } catch (Exception e) {
            logger.error("Failed to create ToolCallbackProvider", e);
            throw e;
        }
    }

    @Bean
    public CommandLineRunner commandRunner(McpSyncServer mcpSyncServer,
            UpdateSignalService updateSignalService) {
        logger.info("Creating Generic Academic MCP CommandLineRunner");

        try {
            McpCommandLineRunner runner =
                    new McpCommandLineRunner(mcpSyncServer, updateSignalService);
            logger.info("Successfully created Generic Academic MCP CommandLineRunner");
            return runner;
        } catch (Exception e) {
            logger.error("Failed to create Generic Academic MCP CommandLineRunner", e);
            throw e;
        }
    }
}
