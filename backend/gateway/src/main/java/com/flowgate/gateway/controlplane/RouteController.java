package com.flowgate.gateway.controlplane;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RouteRepository routes;
    private final UpstreamTargetRepository targets;

    public RouteController(RouteRepository routes, UpstreamTargetRepository targets) {
        this.routes = routes;
        this.targets = targets;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<RouteResponse> list() {
        Map<UUID, List<UpstreamTargetEntity>> byRoute =
                targets.findAll().stream().collect(Collectors.groupingBy(UpstreamTargetEntity::getRouteId));
        return routes.findAll().stream()
                .map(r -> RouteResponse.from(r, byRoute.getOrDefault(r.getId(), List.of())))
                .toList();
    }
}
