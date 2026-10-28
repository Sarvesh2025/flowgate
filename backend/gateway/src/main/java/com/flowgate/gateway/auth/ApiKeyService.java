package com.flowgate.gateway.auth;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApiKeyService {

    public record CreatedKey(UUID id, String name, String prefix, String tier, String apiKey) {}

    public record KeyView(UUID id, String name, String prefix, String tier, boolean revoked) {}

    private final ApiKeyRepository keys;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(ApiKeyRepository keys) {
        this.keys = keys;
    }

    @Transactional(readOnly = true)
    public List<KeyView> list() {
        return keys.findAll().stream()
                .map(e -> new KeyView(e.getId(), e.getName(), e.getPrefix(), e.getTier(), e.isRevoked()))
                .toList();
    }

    @Transactional
    public CreatedKey create(String name, String tier) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        String prefix = HexFormat.of().formatHex(randomBytes(4));
        String secret = HexFormat.of().formatHex(randomBytes(16));
        String apiKey = "fg_" + prefix + "_" + secret;
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setName(name);
        entity.setPrefix(prefix);
        entity.setKeyHash(ApiKeyHasher.sha256(apiKey));
        entity.setTier(tier == null || tier.isBlank() ? "FREE" : tier);
        entity.setRevoked(false);
        keys.save(entity);
        return new CreatedKey(entity.getId(), entity.getName(), prefix, entity.getTier(), apiKey);
    }

    @Transactional
    public CreatedKey rotate(UUID id) {
        ApiKeyEntity entity = keys.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "key not found"));
        entity.setRevoked(true);
        keys.save(entity);
        return create(entity.getName() + "-rotated", entity.getTier());
    }

    @Transactional
    public void revoke(UUID id) {
        ApiKeyEntity entity = keys.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "key not found"));
        entity.setRevoked(true);
        keys.save(entity);
    }

    @Transactional(readOnly = true)
    public ApiKeyPrincipal authenticate(String rawKey) {
        String[] parts = rawKey.split("_", 3);
        if (parts.length != 3 || !"fg".equals(parts[0])) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid api key");
        }
        ApiKeyEntity entity = keys.findByPrefix(parts[1])
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid api key"));
        if (entity.isRevoked() || !ApiKeyHasher.equals(entity.getKeyHash(), ApiKeyHasher.sha256(rawKey))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid api key");
        }
        return new ApiKeyPrincipal(entity.getId(), entity.getPrefix(), entity.getTier());
    }

    private byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        random.nextBytes(bytes);
        return bytes;
    }
}
