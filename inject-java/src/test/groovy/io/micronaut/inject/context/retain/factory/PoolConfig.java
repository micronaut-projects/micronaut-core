package io.micronaut.inject.context.retain.factory;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;

@EachProperty("factory-pools")
@Requires(property = "spec.name", value = "RetainedFactoryProductSpec")
public class PoolConfig {
    public final String name;

    public PoolConfig(@Parameter String name) {
        this.name = name;
    }
}
