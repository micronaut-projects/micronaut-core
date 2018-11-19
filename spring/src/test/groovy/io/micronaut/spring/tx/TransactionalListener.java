package io.micronaut.spring.tx;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.spring.tx.annotation.Transactional;

import javax.inject.Singleton;

@Singleton
public class TransactionalListener implements ApplicationEventListener<FakeEvent> {

    private static int invokeCount = 0;

    @Override
    @Transactional
    public void onApplicationEvent(FakeEvent event) {
        invokeCount++;
        System.out.println("Hello");
    }

    public int invokeCount() {
        return invokeCount;
    }
}
