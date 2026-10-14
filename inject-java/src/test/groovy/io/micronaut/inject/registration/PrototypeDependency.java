package io.micronaut.inject.registration;

import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;

@Prototype
public class PrototypeDependency {

    static int destroyed;

    @PreDestroy
    void destroy() {
        destroyed++;
    }
}
