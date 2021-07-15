package io.micronaut.inject.lifecycle.beanwithprivatepredestroy;

import io.micronaut.context.event.BeanDestroyedEvent;
import io.micronaut.context.event.BeanDestroyedEventListener;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;

@Singleton
public class CDestroyedListener implements BeanDestroyedEventListener<C> {
    private boolean called = true;
    @Override
    public void onDestroyed(BeanDestroyedEvent<C> event) {
        this.called = called;
        Assertions.assertTrue(event.getBean().isClosed());
    }

    public boolean isCalled() {
        return called;
    }
}
