package io.micronaut.inject.provider.lookuppoint;

import io.micronaut.context.BeanDependencyResolver;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;

/**
 * A provider-like object built for the injection point it is injected into, which resolves a dependent on each call
 * through the resolver of its owner, at the segment it was itself resolved at.
 *
 * @param resolver The resolver of the owner
 * @param segment The segment the lookup was resolved at
 * @param definition The definition the lookup resolves
 * @param type The type the lookup resolves the definition as
 * @param eager A registration resolved while the lookup was built, through the resolution context itself
 * @param <T> The type
 */
public record Lookup<T>(BeanDependencyResolver resolver,
                        BeanResolutionContext.Segment<?, ?> segment,
                        BeanDefinition<? extends T> definition,
                        Argument<T> type,
                        BeanRegistration<T> eager) {

    public BeanRegistration<T> get() {
        return resolver.getBeanRegistration(definition, type, segment);
    }
}
