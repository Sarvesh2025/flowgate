package com.flowgate.gateway.controlplane;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rules")
public class RateLimitRuleController {

    private final RateLimitRuleService rules;

    public RateLimitRuleController(RateLimitRuleService rules) {
        this.rules = rules;
    }

    @GetMapping
    public List<RuleResponse> list() {
        return rules.list();
    }

    @GetMapping("/{id}")
    public RuleResponse get(@PathVariable UUID id) {
        return rules.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse create(@RequestBody RuleRequest request) {
        return rules.create(request);
    }

    @PutMapping("/{id}")
    public RuleResponse update(@PathVariable UUID id, @RequestBody RuleRequest request) {
        return rules.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        rules.delete(id);
    }
}
