package com.flowgate.gateway.meta;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    @GetMapping
    public Map<String, String> meta() {
        return Map.of(
                "name", "flowgate-gateway",
                "phase", "1",
                "role", "control-plane-and-data-plane (data plane not wired yet)");
    }
}
