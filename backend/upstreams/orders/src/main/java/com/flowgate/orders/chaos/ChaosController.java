package com.flowgate.orders.chaos;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/chaos")
public class ChaosController {

    private final ChaosSettings settings;

    public ChaosController(ChaosSettings settings) {
        this.settings = settings;
    }

    @GetMapping
    public Map<String, Object> get() {
        return Map.of(
                "delayMs", settings.getDelayMs(),
                "errorRate", settings.getErrorRate(),
                "forceDown", settings.isForceDown());
    }

    @PutMapping
    public Map<String, Object> put(@RequestBody Map<String, Object> body) {
        int delayMs = body.get("delayMs") instanceof Number n ? n.intValue() : settings.getDelayMs();
        double errorRate =
                body.get("errorRate") instanceof Number n ? n.doubleValue() : settings.getErrorRate();
        boolean forceDown =
                body.get("forceDown") instanceof Boolean b ? b : settings.isForceDown();
        settings.update(delayMs, errorRate, forceDown);
        return get();
    }
}
