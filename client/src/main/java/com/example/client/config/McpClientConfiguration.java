package com.example.client.config;

import com.example.client.service.McpToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class McpClientConfiguration {
	@Value("${server.port}")
	private int serverPort;

	@Bean
	public CommandLineRunner mcpClientInitializer(McpToolService mcpToolService) {
		return args -> {
			log.info("Starting MCP Client initialization...");
			mcpToolService.initializeAndFetchTools();
			log.info("University chat is available at http://localhost:{}", serverPort);
		};
	}

	@Bean
	public McpSyncClientCustomizer mcpClientCustomizer() {
		return (name, mcpClientSpec) -> {
			log.info("Configuring MCP client: {}", name);
			mcpClientSpec.toolsChangeConsumer(tv -> {
				log.info("MCP TOOLS CHANGE: {}", tv);
				// The removed latch was here.
			});
		};
	}
}
