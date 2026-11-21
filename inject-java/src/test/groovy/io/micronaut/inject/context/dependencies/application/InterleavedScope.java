package io.micronaut.inject.context.dependencies.application;

import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Holds nothing, as for a prototype, and runs {@link #duringLookup} once when the context looks up a {@link Template}
 * in it: what another caller does on another thread while the recreation of the template runs.
 */
@Singleton
@Requires(property = "spec.name", value = "RecreateRunningApplicationSpec")
public class InterleavedScope implements CustomScope<Interleaved> {

    static volatile Runnable duringLookup;

    @Override
    public Class<Interleaved> annotationType() {
        return Interleaved.class;
    }

    @Override
    public <T> T getOrCreate(BeanCreationContext<T> creationContext) {
        return creationContext.create().bean();
    }

    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<BeanRegistration<T>> findBeanRegistration(T bean) {
        Runnable interleaved = duringLookup;
        if (interleaved != null && bean instanceof Template) {
            duringLookup = null;
            interleaved.run();
        }
        return Optional.empty();
    }
}
