package io.micronaut.inject.lifecycle.beanwithprivatepredestroy;

import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;

@Singleton
public class CPreDestroyEventListener implements BeanPreDestroyEventListener<C> {
    private boolean called = false;
    @Override
    public C onPreDestroy(BeanPreDestroyEvent<C> event) {
        this.called = true;
        Assertions.assertFalse(event.getBean().isClosed());
        return event.getBean();
    }

    public boolean isCalled() {
        return called;
    }

}
