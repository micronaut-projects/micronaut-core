package io.micronaut.inject.lifecycle.proxybeanwithpredestroy;

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
        Assertions.assertEquals(1, C.closed);
    }

    public int getCalled() {
        return called;
    }
}
