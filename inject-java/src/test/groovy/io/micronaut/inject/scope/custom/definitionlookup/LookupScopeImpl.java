package io.micronaut.inject.scope.custom.definitionlookup;

import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class LookupScopeImpl extends AbstractConcurrentCustomScope<LookupScope> {

    public final Map<BeanIdentifier, CreatedBean<?>> beans = new ConcurrentHashMap<>();

    public LookupScopeImpl() {
        super(LookupScope.class);
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
