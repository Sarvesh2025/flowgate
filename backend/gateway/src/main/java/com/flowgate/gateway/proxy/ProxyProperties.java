package com.flowgate.gateway.proxy;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowgate.proxy")
public class ProxyProperties {

    private int connectTimeoutMs = 200;
    private int readTimeoutMs = 2000;
    private long maxBodyBytes = 1_048_576;
    private int cacheTtlSeconds = 5;
    private long healthIntervalMs = 10_000;
    private List<String> ipAllow = new ArrayList<>();

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public long getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(long maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public long getHealthIntervalMs() {
        return healthIntervalMs;
    }

    public void setHealthIntervalMs(long healthIntervalMs) {
        this.healthIntervalMs = healthIntervalMs;
    }

    public List<String> getIpAllow() {
        return ipAllow;
    }

    public void setIpAllow(List<String> ipAllow) {
        this.ipAllow = ipAllow;
    }
}
