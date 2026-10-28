package com.flowgate.gateway.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/keys")
public class ApiKeyController {

    public record CreateKeyRequest(String name, String tier) {}

    private final ApiKeyService keys;

    public ApiKeyController(ApiKeyService keys) {
        this.keys = keys;
    }

    @GetMapping
    public List<ApiKeyService.KeyView> list() {
        return keys.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyService.CreatedKey create(@RequestBody CreateKeyRequest request) {
        return keys.create(request.name(), request.tier());
    }

    @PostMapping("/{id}/rotate")
    public ApiKeyService.CreatedKey rotate(@PathVariable UUID id) {
        return keys.rotate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        keys.revoke(id);
    }
}
