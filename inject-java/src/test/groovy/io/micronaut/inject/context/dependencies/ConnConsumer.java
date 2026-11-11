package io.micronaut.inject.context.dependencies;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;

@EachBean(Conn.class)
@Requires(property = "spec.name", value = "BeanDependencyGraphSpec")
public class ConnConsumer {
    public final Conn conn;

    ConnConsumer(Conn conn) {
        this.conn = conn;
    }
}
