package io.micronaut.inject.scope.custom;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.Singleton;

@Singleton
public class SpecialBeanScope implements CustomScope<SpecialBean> {
    private final Map<BeanIdentifier, CreatedBean<?>> beans = new ConcurrentHashMap<>();

    public Map<BeanIdentifier, CreatedBean<?>> getBeans() {
        return beans;
    }

    @Override
    public Class<SpecialBean> annotationType() {
        return SpecialBean.class;
    }

    @Override
    public <T> T getOrCreate(BeanCreationContext<T> context) {
        return (T) beans.computeIfAbsent(context.id(), key -> context.create()).bean();
    }

    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        final CreatedBean<?> createdBean = beans.remove(identifier);
        if (createdBean != null) {
            createdBean.close();
            return (Optional<T>) Optional.of(createdBean.bean());
        }
        return Optional.empty();
    }
}
