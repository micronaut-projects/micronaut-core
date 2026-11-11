
package io.micronaut.inject.provider.injectionpointowner;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanDependencyResolver;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.beans.injectionpoints.DisposableDependency;
import io.micronaut.inject.provider.AbstractInjectionPointBeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Builds an {@link Owned} with a dependency resolved through the resolver of the bean it is injected into.
 */
public final class OwnedDefinition extends AbstractInjectionPointBeanDefinition<Owned<Object>> {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<Owned<Object>> getBeanType() {
        return (Class) Owned.class;
    }

    @Override
    protected Owned<Object> build(BeanResolutionContext resolutionContext,
                                  BeanContext context,
                                  @Nullable InjectionPoint<?> injectionPoint) {
        BeanDependencyResolver resolver = Objects.requireNonNull(resolutionContext.getDependencyResolver());
        return new Owned<>(resolver, resolver.getBeanRegistration(DisposableDependency.class));
    }
}
