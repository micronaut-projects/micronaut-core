package io.micronaut.inject.context.dependencies;

import io.micronaut.context.BeanDependencyGroup;
import io.micronaut.context.BeanDependencyResolver;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "BeanDependencyGraphSpec")
public class ResolverOwner {
    private final BeanDependencyGroup group;

    ResolverOwner(BeanDependencyResolver resolver) {
        this.group = resolver.createGroup();
    }

    <T> BeanRegistration<T> fresh(BeanDefinition<T> definition) {
        return group.createBeanRegistration(definition);
    }

    <T> T lookup(Class<T> type) {
        return group.getBean(type);
    }
}
