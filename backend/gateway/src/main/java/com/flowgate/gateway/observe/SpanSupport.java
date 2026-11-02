package com.flowgate.gateway.observe;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

@Component
public class SpanSupport {

    private final Tracer tracer;

    public SpanSupport(Tracer tracer) {
        this.tracer = tracer;
    }

    public String traceId() {
        Span span = tracer.currentSpan();
        return span == null ? null : span.context().traceId();
    }

    public void tag(String key, String value) {
        Span span = tracer.currentSpan();
        if (span != null && value != null) {
            span.tag(key, value);
        }
    }

    public Span start(String name) {
        return tracer.nextSpan().name(name).start();
    }

    public Tracer.SpanInScope scope(Span span) {
        return tracer.withSpan(span);
    }
}
