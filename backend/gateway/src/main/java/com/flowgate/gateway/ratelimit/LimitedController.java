package com.flowgate.gateway.ratelimit;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LimitedController {

    @GetMapping("/api/v1/limited")
    public Map<String, String> limited() {
        return Map.of("status", "allowed");
    }
}
