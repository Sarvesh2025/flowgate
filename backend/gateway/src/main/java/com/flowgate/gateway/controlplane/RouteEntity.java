package com.flowgate.gateway.controlplane;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "routes")
public class RouteEntity {

    @Id
    private UUID id;

    @Column(name = "route_key", nullable = false, unique = true)
    private String routeKey;

    @Column(name = "path_pattern", nullable = false)
    private String pathPattern;

    @Column(nullable = false)
    private String methods;

    @Column(name = "rewrite_prefix")
    private String rewritePrefix;

    @Column(name = "lb_algorithm", nullable = false)
    private String lbAlgorithm;

    @Column(name = "canary_percent", nullable = false)
    private int canaryPercent;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (lbAlgorithm == null) {
            lbAlgorithm = "ROUND_ROBIN";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getRouteKey() {
        return routeKey;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public String getMethods() {
        return methods;
    }

    public String getRewritePrefix() {
        return rewritePrefix;
    }

    public String getLbAlgorithm() {
        return lbAlgorithm;
    }

    public int getCanaryPercent() {
        return canaryPercent;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
