package io.micronaut.inject.beans;

import io.micronaut.context.annotation.Context;
import io.micronaut.scheduling.annotation.Async;

@Context
public class ContextScopedInterceptedBean {

    @Async
    void doSomething() {

    }
}