package io.micronaut.inject.provider.lookuppoint;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.InjectionPoint;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

@Red
@Prototype
public class RedService {

    @Inject
    InjectionPoint<?> point;

    boolean destroyed;

    @PreDestroy
    void close() {
        destroyed = true;
    }
}
