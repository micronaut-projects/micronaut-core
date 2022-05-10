package io.micronaut.inject.lifecycle.proxytargetbeanprototypewithpredestroy;

import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;

@Singleton
public class CPreDestroyEventListener implements BeanPreDestroyEventListener<C> {
    private int called = 0;
    @Override
    public C onPreDestroy(BeanPreDestroyEvent<C> event) {
        this.called++;
        Assertions.assertEquals(C.closed, 0);
        return event.getBean();
    }

    public int getCalled() {
        return called;
    }

}
