package io.micronaut.aop.runtimetarget;

import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;

/**
 * Receives proxies while the resolution that creates them goes on, and calls one of them in that resolution.
 */
@Prototype
public class ProxyHolder {

    private final InjectingTarget perCall;

    // the second proxy is only received, so that the resolution creating this holder also creates it
    @SuppressWarnings("java:S1172")
    public ProxyHolder(InjectingTarget perCall, CachingTarget oneTarget) {
        this.perCall = perCall;
    }

    @PostConstruct
    void initialize() {
        perCall.injected();
    }
}
