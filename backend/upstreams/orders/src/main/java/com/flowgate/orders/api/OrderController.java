package com.flowgate.orders.api;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "orders");
    }

    @GetMapping("/api/orders")
    public List<Map<String, Object>> list() {
        return List.of(
                Map.of("id", "ord-1", "item", "keyboard", "total", 49),
                Map.of("id", "ord-2", "item", "mouse", "total", 19));
    }

    @GetMapping("/api/orders/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return Map.of("id", id, "item", "keyboard", "total", 49);
    }
}
