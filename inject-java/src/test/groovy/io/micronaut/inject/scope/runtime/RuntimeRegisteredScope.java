package io.micronaut.inject.scope.runtime;

import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.inject.BeanIdentifier;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deliberately not a bean: a test registers it through {@link io.micronaut.context.RuntimeBeanDefinition}.
 */
public class RuntimeRegisteredScope implements CustomScope<RuntimeRegistered> {
    private final Map<BeanIdentifier, CreatedBean<?>> beans = new ConcurrentHashMap<>();
    private final AtomicInteger created = new AtomicInteger();

    public int getCreated() {
        return created.get();
    }

    @Override
    public Class<RuntimeRegistered> annotationType() {
        return RuntimeRegistered.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrCreate(BeanCreationContext<T> context) {
        return (T) beans.computeIfAbsent(context.id(), key -> {
            created.incrementAndGet();
            return context.create();
        }).bean();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        CreatedBean<?> createdBean = beans.remove(identifier);
        if (createdBean != null) {
            createdBean.close();
            return (Optional<T>) Optional.of(createdBean.bean());
        }
        return Optional.empty();
    }
}
