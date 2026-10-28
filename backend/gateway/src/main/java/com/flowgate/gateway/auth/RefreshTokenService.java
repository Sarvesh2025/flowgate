package com.flowgate.gateway.auth;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RefreshTokenService {

    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;

    public RefreshTokenService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String issue(String username) {
        String familyId = UUID.randomUUID().toString();
        String jti = UUID.randomUUID().toString();
        redis.opsForValue().set(familyKey(familyId), "ALIVE", TTL);
        redis.opsForValue().set(rtKey(jti), familyId + ":" + username + ":ACTIVE", TTL);
        return jti;
    }

    public Rotated rotate(String jti) {
        String payload = redis.opsForValue().get(rtKey(jti));
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh");
        }
        String[] parts = payload.split(":", 3);
        String familyId = parts[0];
        String username = parts[1];
        String status = parts[2];
        String family = redis.opsForValue().get(familyKey(familyId));
        if (!"ALIVE".equals(family)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "family revoked");
        }
        if ("ROTATED".equals(status)) {
            redis.opsForValue().set(familyKey(familyId), "REVOKED", TTL);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh reuse detected");
        }
        redis.opsForValue().set(rtKey(jti), familyId + ":" + username + ":ROTATED", TTL);
        String next = UUID.randomUUID().toString();
        redis.opsForValue().set(rtKey(next), familyId + ":" + username + ":ACTIVE", TTL);
        redis.expire(familyKey(familyId), TTL);
        return new Rotated(username, next);
    }

    public void revokeCookie(String jti) {
        if (jti == null) {
            return;
        }
        String payload = redis.opsForValue().get(rtKey(jti));
        if (payload == null) {
            return;
        }
        String familyId = payload.split(":", 3)[0];
        redis.opsForValue().set(familyKey(familyId), "REVOKED", TTL);
        redis.delete(rtKey(jti));
    }

    public record Rotated(String username, String newJti) {}

    private static String rtKey(String jti) {
        return "auth:rt:" + jti;
    }

    private static String familyKey(String familyId) {
        return "auth:fam:" + familyId;
    }
}
