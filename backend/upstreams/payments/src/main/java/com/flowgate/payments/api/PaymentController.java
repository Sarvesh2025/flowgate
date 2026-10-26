package com.flowgate.payments.api;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "payments");
    }

    @GetMapping("/api/payments")
    public List<Map<String, Object>> list() {
        return List.of(
                Map.of("id", "pay-1", "orderId", "ord-1", "status", "SETTLED"),
                Map.of("id", "pay-2", "orderId", "ord-2", "status", "PENDING"));
    }

    @GetMapping("/api/payments/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return Map.of("id", id, "orderId", "ord-1", "status", "SETTLED");
    }
}
