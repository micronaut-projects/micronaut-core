package io.micronaut.inject.context.deadlock;

import io.micronaut.context.BeanContext;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class BeanA {
    @Inject
    BeanContext context;

    @PostConstruct
    void createB() throws Exception {
        Thread thread = new Thread(() -> {
            context.getBean(BeanB.class).visitedFromA = true;
        });
        thread.start();
        thread.join();
    }
}