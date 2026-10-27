package com.flowgate.gateway.controlplane;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        AuditEventEntity row = new AuditEventEntity();
        row.setActor("anonymous");
        row.setAction(action);
        row.setEntityType("rate_limit_rule");
        row.setEntityId(entityId);
        row.setBeforeJson(write(before));
        row.setAfterJson(write(after));
        audits.save(row);
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
