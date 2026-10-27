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
@Table(name = "rate_limit_rules")
public class RateLimitRuleEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "route_id")
    private UUID routeId;

    @Column(nullable = false)
    private String scope;

    @Column(nullable = false)
    private String algorithm;

    @Column(name = "limit_count", nullable = false)
    private int limitCount;

    @Column(name = "window_ms", nullable = false)
    private int windowMs;

    @Column(nullable = false)
    private int priority;

    @Column(name = "redis_down_policy", nullable = false)
    private String redisDownPolicy;

    @Column(name = "quota_limit")
    private Integer quotaLimit;

    @Column(name = "quota_period")
    private String quotaPeriod;

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
        if (scope == null) {
            scope = "ROUTE";
        }
        if (redisDownPolicy == null) {
            redisDownPolicy = "FAIL_CLOSED";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getRouteId() {
        return routeId;
    }

    public void setRouteId(UUID routeId) {
        this.routeId = routeId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public int getLimitCount() {
        return limitCount;
    }

    public void setLimitCount(int limitCount) {
        this.limitCount = limitCount;
    }

    public int getWindowMs() {
        return windowMs;
    }

    public void setWindowMs(int windowMs) {
        this.windowMs = windowMs;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getRedisDownPolicy() {
        return redisDownPolicy;
    }

    public void setRedisDownPolicy(String redisDownPolicy) {
        this.redisDownPolicy = redisDownPolicy;
    }

    public Integer getQuotaLimit() {
        return quotaLimit;
    }

    public void setQuotaLimit(Integer quotaLimit) {
        this.quotaLimit = quotaLimit;
    }

    public String getQuotaPeriod() {
        return quotaPeriod;
    }

    public void setQuotaPeriod(String quotaPeriod) {
        this.quotaPeriod = quotaPeriod;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
