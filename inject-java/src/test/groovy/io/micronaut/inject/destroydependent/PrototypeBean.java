package io.micronaut.inject.destroydependent;

import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;

@Prototype
public class PrototypeBean {

    static int destroyed;

    final NestedDependency dependency;

    public PrototypeBean(NestedDependency dependency) {
        this.dependency = dependency;
    }

    @PreDestroy
    void destroy() {
        destroyed++;
    }
}
