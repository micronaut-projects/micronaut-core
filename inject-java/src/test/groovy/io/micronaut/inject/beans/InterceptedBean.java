package io.micronaut.inject.beans;

import io.micronaut.scheduling.annotation.Async;

import javax.inject.Singleton;

@Singleton
public class InterceptedBean {

    @Async
    void doSomething() {

    }
}
