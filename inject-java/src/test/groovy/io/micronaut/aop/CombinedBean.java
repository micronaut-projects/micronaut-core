package io.micronaut.aop;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;

import javax.inject.Singleton;

@Requires(property = "spec.name", value = "CombinedBeanSpec")
@Singleton
public class CombinedBean {

    @Logged
    void methodOne() {

    }

    @Scheduled(fixedRate = "10s")
    void methodTwo() {

    }
}
