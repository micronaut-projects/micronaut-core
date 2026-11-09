package io.micronaut.aop.around.concurrenttarget;

import io.micronaut.aop.around.proxytarget.Mutating;
import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Prototype
@Requires(property = "spec.name", value = "ConcurrentProxyTargetTest")
@RuntimeProxy(value = CapturingRuntimeProxy.class, proxyTarget = true)
public class PrototypeService {

    static final Set<Object> DESTROYED = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    @Inject
    RepositoryA repositoryA;

    @Inject
    RepositoryB repositoryB;

    @Inject
    RepositoryC repositoryC;

    @PreDestroy
    void destroy() {
        DESTROYED.add(this);
    }

    @Mutating("name")
    public String test(String name) {
        return name;
    }

    @Prototype
    @Requires(property = "spec.name", value = "ConcurrentProxyTargetTest")
    public static class RepositoryA {
        @Inject
        RepositoryC repositoryC;
    }

    @Prototype
    @Requires(property = "spec.name", value = "ConcurrentProxyTargetTest")
    public static class RepositoryB {
        @Inject
        RepositoryC repositoryC;
    }

    @Prototype
    @Requires(property = "spec.name", value = "ConcurrentProxyTargetTest")
    public static class RepositoryC {
    }
}
