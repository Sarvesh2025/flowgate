package com.flowgate.gateway.auth;

import java.util.UUID;

public record ApiKeyPrincipal(UUID id, String prefix, String tier) {}
