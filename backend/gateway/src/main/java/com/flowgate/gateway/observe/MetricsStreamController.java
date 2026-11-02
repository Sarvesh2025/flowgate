package com.flowgate.gateway.observe;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsStreamController {

    private final MetricsRing ring;
    private final List<SseEmitter> clients = new CopyOnWriteArrayList<>();

    public MetricsStreamController(MetricsRing ring) {
        this.ring = ring;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(e -> clients.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("snapshot").data(ring.snapshot()));
        } catch (IOException ex) {
            clients.remove(emitter);
            emitter.complete();
        }
        return emitter;
    }

    @Scheduled(fixedRate = 1000)
    public void push() {
        MetricsRing.Sample sample = ring.latest();
        for (SseEmitter emitter : clients) {
            try {
                emitter.send(SseEmitter.event().name("tick").data(sample));
            } catch (Exception ex) {
                clients.remove(emitter);
                emitter.complete();
            }
        }
    }
}
