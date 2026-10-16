package io.micronaut.inject.context.retain.factory;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

/**
 * Wraps each pool as Micronaut Data's {@code ContextualAwareDataSource} does: the wrapper is what the context
 * registers, and it holds the context's bean locator.
 */
@Singleton
@Requires(property = "spec.name", value = "RetainedFactoryProductSpec")
public final class ContextualPools implements BeanCreatedEventListener<DataPool> {
    private final BeanLocator beanLocator;

    public ContextualPools(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public DataPool onCreated(BeanCreatedEvent<DataPool> event) {
        return new Wrapper(event.getBean());
    }

    /**
     * The wrapper registered as the pool.
     */
    public final class Wrapper implements DataPool {
        public final DataPool target;

        Wrapper(DataPool target) {
            this.target = target;
        }

        public BeanLocator locator() {
            return beanLocator;
        }

        @Override
        public String name() {
            return target.name();
        }

        @Override
        public boolean isClosed() {
            return target.isClosed();
        }

        @Override
        public void close() {
            target.close();
        }
    }
}
