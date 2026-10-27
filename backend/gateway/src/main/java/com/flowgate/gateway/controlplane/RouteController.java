package com.flowgate.gateway.controlplane;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RouteRepository routes;

    public RouteController(RouteRepository routes) {
        this.routes = routes;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<RouteResponse> list() {
        return routes.findAll().stream().map(RouteResponse::from).toList();
    }
}
