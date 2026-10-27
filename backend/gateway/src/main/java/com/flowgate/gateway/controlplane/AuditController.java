package com.flowgate.gateway.controlplane;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    public record AuditResponse(
            UUID id,
            String actor,
            String action,
            String entityType,
            String entityId,
            String beforeJson,
            String afterJson,
            Instant at) {}

    private final AuditEventRepository audits;

    public AuditController(AuditEventRepository audits) {
        this.audits = audits;
    }

    @GetMapping
    public List<AuditResponse> list() {
        return audits.findTop50ByOrderByAtDesc().stream()
                .map(
                        e -> new AuditResponse(
                                e.getId(),
                                e.getActor(),
                                e.getAction(),
                                e.getEntityType(),
                                e.getEntityId(),
                                e.getBeforeJson(),
                                e.getAfterJson(),
                                e.getAt()))
                .toList();
    }
}
