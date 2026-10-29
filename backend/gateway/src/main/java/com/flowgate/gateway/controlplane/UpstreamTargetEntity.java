package com.flowgate.gateway.controlplane;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "upstream_targets")
public class UpstreamTargetEntity {

    @Id
    private UUID id;

    @Column(name = "route_id", nullable = false)
    private UUID routeId;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private int weight;

    @Column(nullable = false)
    private boolean canary;

    @Column(nullable = false)
    private boolean healthy;

    public UUID getId() {
        return id;
    }

    public UUID getRouteId() {
        return routeId;
    }

    public String getUrl() {
        return url;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isCanary() {
        return canary;
    }

    public boolean isHealthy() {
        return healthy;
    }
}
