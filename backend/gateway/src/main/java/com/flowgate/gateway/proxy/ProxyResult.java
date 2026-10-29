package com.flowgate.gateway.proxy;

import org.springframework.http.HttpHeaders;

record ProxyResult(
        int status, HttpHeaders headers, byte[] body, String upstream, boolean cacheHit, boolean canary) {

    static ProxyResult fallback(int status, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        return new ProxyResult(status, headers, json.getBytes(java.nio.charset.StandardCharsets.UTF_8), "", false, false);
    }
}
