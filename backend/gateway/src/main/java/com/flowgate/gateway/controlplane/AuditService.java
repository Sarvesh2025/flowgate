package com.flowgate.gateway.controlplane;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditEventRepository audits;
    private final ObjectMapper json;

    public AuditService(AuditEventRepository audits, ObjectMapper json) {
        this.audits = audits;
        this.json = json;
    }

    public void record(String action, String entityId, Object before, Object after) {
        record(action, "rate_limit_rule", entityId, before, after);
    }

    public void record(String action, String entityType, String entityId, Object before, Object after) {
        AuditEventEntity row = new AuditEventEntity();
        row.setActor(currentActor());
        row.setAction(action);
        row.setEntityType(entityType);
        row.setEntityId(entityId);
        row.setBeforeJson(write(before));
        row.setAfterJson(write(after));
        audits.save(row);
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return "anonymous";
        }
        return String.valueOf(auth.getPrincipal());
    }

    private String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("audit json", e);
        }
    }
}
