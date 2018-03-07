package io.micronaut.context;


import javax.inject.Provider;

/**
 * A default component provider
 */
class UnresolvedProvider<T> implements Provider<T> {

    private final Class<T> beanType;
    private final BeanContext context;

    UnresolvedProvider(Class<T> beanType, BeanContext context) {
        this.beanType = beanType;
        this.context = context;
    }

    @Override
    public T get() {
        return context.getBean(beanType);
    }
}
