package io.micronaut.inject.beans;

import io.micronaut.context.annotation.Parallel;
import io.micronaut.scheduling.annotation.Async;

import javax.inject.Singleton;

@Singleton
@Parallel
public class ParallelBean {

    @Async
    void doSomething() {

    }
}
