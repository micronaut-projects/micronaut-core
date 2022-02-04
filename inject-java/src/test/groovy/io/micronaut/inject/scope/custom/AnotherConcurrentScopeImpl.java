package io.micronaut.inject.scope.custom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.Singleton;

@Singleton
public class AnotherConcurrentScopeImpl extends AbstractConcurrentCustomScope<AnotherConcurrentScope> {

    private Map<BeanIdentifier, CreatedBean<?>> scope;

    public AnotherConcurrentScopeImpl() {
        super(AnotherConcurrentScope.class);
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) {
        if (scope == null) {
            scope = new ConcurrentHashMap<>();
        }
        return scope;
    }

    @Override
    public void close() {
        for (CreatedBean<?> createdBean : scope.values()) {
            createdBean.close();
        }
    }
}
