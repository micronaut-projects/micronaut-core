package io.micronaut.inject.context.dependencies;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;

@EachBean(Conn.class)
@Requires(property = "spec.name", value = "BeanDependencyGraphSpec")
public class ConnProviderConsumer {
    public final BeanProvider<Conn> conn;

    ConnProviderConsumer(BeanProvider<Conn> conn) {
        this.conn = conn;
    }
}
