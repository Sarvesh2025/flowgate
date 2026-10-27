package com.flowgate.gateway.controlplane;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<RouteEntity, UUID> {}
