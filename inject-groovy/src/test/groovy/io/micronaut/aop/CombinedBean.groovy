package io.micronaut.aop

import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton

@Requires(property = "spec.name", value = "CombinedBeanSpec")
@Singleton
class CombinedBean {

    @Logged
    void methodOne() {

    }

    @Scheduled(fixedRate = "10s")
    void methodTwo() {

    }
}
