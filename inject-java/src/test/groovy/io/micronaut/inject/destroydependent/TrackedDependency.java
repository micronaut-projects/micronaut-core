package io.micronaut.inject.destroydependent;

import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;

@Prototype
public class TrackedDependency {

    static final List<TrackedDependency> CREATED = new ArrayList<>();
    static final List<TrackedDependency> DESTROYED = new ArrayList<>();

    @PostConstruct
    void create() {
        CREATED.add(this);
    }

    @PreDestroy
    void destroy() {
        DESTROYED.add(this);
    }
}
