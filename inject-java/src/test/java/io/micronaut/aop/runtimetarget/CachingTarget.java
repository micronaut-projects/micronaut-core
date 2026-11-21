package io.micronaut.aop.runtimetarget;

import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A proxied prototype whose proxy resolves its one target while the proxy is created.
 */
@Prototype
@Advised
@RuntimeProxy(value = OneTargetProxyCreator.class, proxyTarget = true)
public class CachingTarget {

    static final AtomicInteger TARGETS_DESTROYED = new AtomicInteger();

    @Inject
    void setDependency(Dependency dependency) {
        // only received: the dependency is owned through the target, whose destruction must release it
    }

    @PreDestroy
    void destroy() {
        // the proxy is a subclass, with the same lifecycle methods
        if (getClass() == CachingTarget.class) {
            TARGETS_DESTROYED.incrementAndGet();
        }
    }

    /**
     * What the target receives, which is owned through the target.
     */
    @Prototype
    public static class Dependency {

        static final AtomicInteger CREATED = new AtomicInteger();
        static final AtomicInteger DESTROYED = new AtomicInteger();

        public Dependency() {
            CREATED.incrementAndGet();
        }

        @PreDestroy
        void destroy() {
            DESTROYED.incrementAndGet();
        }
    }
}
