package com.example.client.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThreadPoolConfig {

	@Bean("asyncTaskExecutor")
	public TaskExecutor asyncTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(5); // Start with 5 threads
		executor.setMaxPoolSize(20); // Allow up to 20 threads
		executor.setQueueCapacity(100); // Queue up to 100 tasks
		executor.setThreadNamePrefix("AsyncChat-");
		executor.initialize();
		return executor;
	}
}
