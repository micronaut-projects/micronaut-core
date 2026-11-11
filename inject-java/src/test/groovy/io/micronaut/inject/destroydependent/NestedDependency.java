package io.micronaut.inject.destroydependent;

import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;

@Prototype
public class NestedDependency {

    static int destroyed;

    @PreDestroy
    void destroy() {
        destroyed++;
    }
}
