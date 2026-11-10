package io.micronaut.aop.runtimetarget;

import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.context.annotation.Prototype;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A proxied prototype whose target calls its own proxy from its constructor, as many times as the test allows.
 */
@Prototype
@Advised
@RuntimeProxy(value = PerCallTargetProxyCreator.class, proxyTarget = true)
public class SelfCallingTarget {

    static final AtomicInteger CREATED = new AtomicInteger();
    static volatile SelfCallingTarget proxy;
    static volatile int limit;

    public SelfCallingTarget() {
        // the proxy is a subclass, which is created before the test sets it here
        if (proxy != null && CREATED.incrementAndGet() < limit) {
            proxy.ready();
        }
    }

    public boolean ready() {
        return true;
    }
}
