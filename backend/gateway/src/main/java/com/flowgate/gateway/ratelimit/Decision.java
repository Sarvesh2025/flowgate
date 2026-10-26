package com.flowgate.gateway.ratelimit;

public record Decision(boolean allowed, long remaining) {}
