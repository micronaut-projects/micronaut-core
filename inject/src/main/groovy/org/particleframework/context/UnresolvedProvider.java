package org.particleframework.context;


import javax.inject.Provider;

/**
 * A default component provider
 */
class UnresolvedProvider<T> implements Provider<T> {

    private final Class<T> beanType;
    private final Context context;

    public UnresolvedProvider(Class<T> beanType, Context context) {
        this.beanType = beanType;
        this.context = context;
    }

    @Override
    public T get() {
        return context.getBean(beanType);
    }
}
