package io.micronaut.inject.context.retain.resolver;

import io.micronaut.context.BeanDependencyResolver;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Makes the connection with what it resolves through a resolver, as a messaging connection resolves the executor its
 * configuration names.
 */
@Factory
@Requires(property = "spec.name", value = "RetainedResolverSpec")
public class ConnectionFactory {

    @Singleton
    Connection connection(BeanDependencyResolver resolver) {
        return new Connection(resolver.getBean(Channel.class), resolver.getBean(Broker.class), resolver.getBean(ConnectionSettings.class).getTimeout());
    }
}
