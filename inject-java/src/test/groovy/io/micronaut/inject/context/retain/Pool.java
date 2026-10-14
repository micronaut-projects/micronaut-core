package io.micronaut.inject.context.retain;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@EachProperty("pools")
@Requires(property = "spec.name", value = "RetainedRegistrationsSpec")
public class Pool {
    public static final List<String> DESTROYED = new CopyOnWriteArrayList<>();
    public final String name;

    public Pool(@Parameter String name) {
        this.name = name;
    }

    @PreDestroy
    void close() {
        DESTROYED.add(name);
    }
}
