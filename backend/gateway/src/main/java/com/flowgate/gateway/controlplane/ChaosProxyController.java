package com.flowgate.gateway.controlplane;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/chaos")
public class ChaosProxyController {

    private static final Map<String, String> SERVICES =
            Map.of(
                    "orders", "http://localhost:8081/internal/chaos",
                    "payments", "http://localhost:8082/internal/chaos");

    private final WebClient client;

    public ChaosProxyController(@Qualifier("healthWebClient") WebClient healthWebClient) {
        this.client = healthWebClient;
    }

    @GetMapping("/{service}")
    public Map<String, Object> get(@PathVariable String service) {
        return exchange(service, null);
    }

    @PutMapping("/{service}")
    public Map<String, Object> put(@PathVariable String service, @RequestBody Map<String, Object> body) {
        return exchange(service, body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchange(String service, Map<String, Object> body) {
        String url = SERVICES.get(service);
        if (url == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown chaos service");
        }
        try {
            var spec = body == null ? client.get().uri(url) : client.put().uri(url).bodyValue(body);
            Map<String, Object> result =
                    spec.retrieve().bodyToMono(Map.class).block(Duration.ofSeconds(2));
            if (result == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "empty chaos response");
            }
            return result;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "chaos upstream unreachable: " + service);
        }
    }
}
