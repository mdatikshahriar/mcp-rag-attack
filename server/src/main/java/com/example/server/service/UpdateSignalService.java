package com.example.server.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@AllArgsConstructor
public class UpdateSignalService {
    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicBoolean signalSent = new AtomicBoolean(false);
    private final AtomicLong signalCount = new AtomicLong(0);
    @Getter
    private volatile LocalDateTime lastSignalTime;

    public UpdateSignalService() {
        log.info("Academic UpdateSignalService initialized");
    }

    public void signalUpdate() {
        log.info("Sending update signal to Academic MCP server");

        try {
            long currentCount = signalCount.incrementAndGet();
            lastSignalTime = LocalDateTime.now();

            if (signalSent.compareAndSet(false, true)) {
                latch.countDown();
                log.info("Update signal sent successfully - Signal #{} at {}", currentCount,
                        lastSignalTime);
            } else {
                log.warn(
                        "Additional update signal received (Signal #{}) - latch already triggered at {}",
                        currentCount, lastSignalTime);
            }

        } catch (Exception e) {
            log.error("Error sending update signal", e);
            throw e;
        }
    }

    public boolean awaitUpdate(long timeout, TimeUnit unit) throws InterruptedException {
        log.info("Waiting for update signal with timeout: {} {}", timeout, unit);
        boolean received = latch.await(timeout, unit);

        if (received) {
            log.info("Update signal received within timeout period");
        } else {
            log.warn("Timeout waiting for update signal after {} {}", timeout, unit);
        }

        return received;
    }

    public boolean isSignalSent() {
        return signalSent.get();
    }

    public long getSignalCount() {
        return signalCount.get();
    }
}
