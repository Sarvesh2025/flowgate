package com.flowgate.orders.chaos;

import org.springframework.stereotype.Component;

/**
 * In-memory chaos knobs. Default is off. ADMIN wiring comes later;
 * for Phase 1 you can PUT /internal/chaos while experimenting.
 */
@Component
public class ChaosSettings {

    private volatile int delayMs = 0;
    private volatile double errorRate = 0.0;
    private volatile boolean forceDown = false;

    public int getDelayMs() {
        return delayMs;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public boolean isForceDown() {
        return forceDown;
    }

    public void update(int delayMs, double errorRate, boolean forceDown) {
        this.delayMs = Math.max(delayMs, 0);
        this.errorRate = Math.min(Math.max(errorRate, 0.0), 1.0);
        this.forceDown = forceDown;
    }
}
