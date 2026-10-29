package com.flowgate.gateway.proxy;

final class UpstreamFailure extends RuntimeException {

    private final int status;

    UpstreamFailure(int status, String message) {
        super(message);
        this.status = status;
    }

    int status() {
        return status;
    }
}
