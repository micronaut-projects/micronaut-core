package io.micronaut.inject.registration;

import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;

@Prototype
public class PrototypeService {

    static int created;
    static int destroyed;

    final PrototypeDependency dependency;

    public PrototypeService(PrototypeDependency dependency) {
        this.dependency = dependency;
        created++;
    }

    @PreDestroy
    void destroy() {
        destroyed++;
    }
}
