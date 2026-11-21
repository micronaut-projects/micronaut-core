package io.micronaut.inject.any.qualifier;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Requires(property = "spec.name", value = "AnyQualifierSpec")
public class AnyPerBeanScopeImpl extends AbstractConcurrentCustomScope<AnyPerBeanScope> {

    private final Map<BeanIdentifier, CreatedBean<?>> beans = new ConcurrentHashMap<>();

    public AnyPerBeanScopeImpl() {
        super(AnyPerBeanScope.class, true);
    }

    public Map<BeanIdentifier, CreatedBean<?>> getBeans() {
        return beans;
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) {
        return beans;
    }

    @Override
    public void close() {
        destroyScope(beans);
    }
}
