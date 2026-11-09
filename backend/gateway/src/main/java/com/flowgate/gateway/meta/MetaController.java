package com.flowgate.gateway.meta;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    private final String instanceId;

    public MetaController(@Value("${flowgate.instance-id:a}") String instanceId) {
        this.instanceId = instanceId;
    }

    @GetMapping
    public Map<String, String> meta() {
        return Map.of(
                "name", "flowgate-gateway",
                "phase", "10",
                "instance", instanceId,
                "role", "control-plane-and-data-plane");
    }
}
