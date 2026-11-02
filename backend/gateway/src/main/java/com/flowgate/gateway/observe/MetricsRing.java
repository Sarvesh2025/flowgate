package com.flowgate.gateway.observe;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MetricsRing {

    public record Sample(Instant at, double proxy, double rejected) {}

    private final GatewayMetrics metrics;
    private final ArrayBlockingQueue<Sample> ring = new ArrayBlockingQueue<>(256);

    public MetricsRing(GatewayMetrics metrics) {
        this.metrics = metrics;
    }

    @Scheduled(fixedRate = 1000)
    public void tick() {
        Sample sample = new Sample(Instant.now(), metrics.proxyCount(), metrics.rejectedCount());
        if (!ring.offer(sample)) {
            ring.poll();
            ring.offer(sample);
        }
    }

    public List<Sample> snapshot() {
        return new ArrayList<>(ring);
    }

    public Sample latest() {
        Sample last = null;
        for (Sample s : ring) {
            last = s;
        }
        if (last == null) {
            return new Sample(Instant.now(), metrics.proxyCount(), metrics.rejectedCount());
        }
        return last;
    }
}
