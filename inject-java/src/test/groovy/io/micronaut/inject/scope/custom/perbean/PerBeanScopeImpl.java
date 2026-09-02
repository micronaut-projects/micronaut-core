package io.micronaut.inject.scope.custom.perbean;

import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class PerBeanScopeImpl extends AbstractConcurrentCustomScope<PerBeanScope> {

    private final Map<BeanIdentifier, CreatedBean<?>> beans = new ConcurrentHashMap<>();

    public PerBeanScopeImpl() {
        super(PerBeanScope.class, true);
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
