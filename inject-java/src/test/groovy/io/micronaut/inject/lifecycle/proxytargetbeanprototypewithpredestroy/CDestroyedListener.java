package io.micronaut.inject.lifecycle.proxytargetbeanprototypewithpredestroy;

import io.micronaut.context.event.BeanDestroyedEvent;
import io.micronaut.context.event.BeanDestroyedEventListener;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;

@Singleton
public class CDestroyedListener implements BeanDestroyedEventListener<C> {
    private int called = 0;
    @Override
    public void onDestroyed(BeanDestroyedEvent<C> event) {
        this.called++;
        Assertions.assertEquals(C.closed, 1);
    }

    public int getCalled() {
        return called;
    }
}
