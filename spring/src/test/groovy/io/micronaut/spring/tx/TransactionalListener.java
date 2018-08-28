package io.micronaut.spring.tx;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.scheduling.annotation.Async;
import io.micronaut.spring.tx.annotation.Transactional;

import javax.inject.Singleton;

@Singleton
public class TransactionalListener implements ApplicationEventListener<StartupEvent> {

    private static int invokeCount = 0;

    public TransactionalListener(ApplicationContext ctx) {

    }

    @Override
    @Transactional
    public void onApplicationEvent(StartupEvent event) {
        invokeCount++;
        System.out.println("Hello");
    }

    @Async
    void asyncMethod() {

    }

    public int invokeCount() {
        return invokeCount;
    }
}
