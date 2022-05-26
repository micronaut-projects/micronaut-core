package io.micronaut.aop.named

import io.micronaut.aop.Logged
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Parameter
import io.micronaut.runtime.context.scope.Refreshable
import jakarta.inject.Named
import jakarta.inject.Singleton

@Factory
class NamedFactory {

    @EachProperty(value = "aop.test.named", primary = "default")
    @Refreshable
    NamedInterface namedInterface(@Parameter String name) {
        () -> name
    }

    @Named("first")
    @Logged
    @Singleton
    OtherInterface first() {
        () -> "first"
    }

    @Named("second")
    @Logged
    @Singleton
    OtherInterface second() {
        () -> "second"
    }

    @EachProperty("other.interfaces")
    OtherInterface third(Config config, @Parameter String name) {
        () -> name
    }
}
