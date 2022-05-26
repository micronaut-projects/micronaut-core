package io.micronaut.inject.lifecycle.beanwithpredestroy;

import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import org.junit.jupiter.api.Assertions;

import jakarta.inject.Singleton;

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
