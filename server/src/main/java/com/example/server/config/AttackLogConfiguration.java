package com.example.server.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AttackLogConfiguration {

    // This method will be triggered once the Spring application context is initialized.
    @EventListener(ContextRefreshedEvent.class)
    public void setupAttackLogger() {
        System.out.println("Programmatically configuring dedicated attack logger...");

        // 1. Get the underlying LoggerContext from SLF4J.
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        // 2. Create the Pattern Encoder for our log messages.
        //    The pattern "%msg%n" means "only the message content, followed by a new line".
        //    This ensures the file contains clean JSON.
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%msg%n");
        encoder.start(); // Always remember to start the component.

        // 3. Create the File Appender, which directs logs to a file.
        FileAppender fileAppender = new FileAppender();
        fileAppender.setContext(context);
        fileAppender.setName("ATTACK_FILE_APPENDER");
        fileAppender.setFile("logs/attack.log"); // The destination log file.
        fileAppender.setAppend(false); // Overwrite the file on each application start.
        fileAppender.setEncoder(encoder); // Assign the encoder to the appender.
        fileAppender.start(); // Start the appender.

        // 4. Get the specific logger we want to configure.
        //    The name "attack.logger" must match what's used in LookupService.
        Logger attackLogger = context.getLogger("attack.logger");

        // 5. Set its level.
        attackLogger.setLevel(Level.INFO);

        // 6. CRITICAL: Disable additivity. This prevents logs sent to "attack.logger"
        //    from also being sent to the parent/root logger (i.e., the console).
        attackLogger.setAdditive(false);

        // 7. Attach our new file appender to this specific logger.
        attackLogger.addAppender(fileAppender);

        System.out.println("✅ Attack logger configured to write to logs/attack.log");
    }
}
