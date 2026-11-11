
package io.micronaut.inject.provider.injectionpointowner;

import io.micronaut.context.BeanDependencyResolver;
import io.micronaut.context.BeanRegistration;
import io.micronaut.inject.beans.injectionpoints.DisposableDependency;

/**
 * A bean built for the injection point it is injected into, holding a dependency it resolved on behalf of the bean
 * it is injected into.
 *
 * @param resolver     The resolver of the bean it is injected into
 * @param registration The dependency the resolver created
 * @param <T>          The type argument of the injection point
 */
public record Owned<T>(BeanDependencyResolver resolver, BeanRegistration<DisposableDependency> registration) {
}
