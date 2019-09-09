package io.micronaut.aop.named;

import io.micronaut.aop.Logged;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.runtime.context.scope.Refreshable;

import javax.inject.Named;
import javax.inject.Singleton;

@Factory
public class NamedFactory {

    @EachProperty(value = "aop.test.named", primary = "default")
    @Refreshable
    NamedInterface namedInterface(@Parameter String name) {
        return () -> name;
    }


    @Named("first")
    @Logged
    @Singleton
    OtherInterface first() {
        return () -> "first";
    }

    @Named("second")
    @Logged
    @Singleton
    OtherInterface second() {
        return () -> "second";
    }

    @EachProperty("other.interfaces")
    OtherInterface third(Config config, @Parameter String name) {
        return () -> name;
    }
}
