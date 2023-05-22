package io.micronaut.aop.adapter.intercepted;

import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Singleton;

@Singleton
class MyBean {

    private final ApplicationEventPublisher applicationEventPublisher;
    long count = 0;

    MyBean(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    void triggerEvent() {
        applicationEventPublisher.publishEvent(new TheEvent());
    }

    @TransactionalEventListener
    void test(TheEvent theEvent) {
        count++;
    }

}
