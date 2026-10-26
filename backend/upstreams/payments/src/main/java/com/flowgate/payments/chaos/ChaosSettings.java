package com.flowgate.payments.chaos;

import org.springframework.stereotype.Component;

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
