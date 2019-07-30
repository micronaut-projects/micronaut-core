package io.micronaut.aop.named;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.runtime.context.scope.Refreshable;

@Factory
public class NamedFactory {

    @EachProperty(value = "aop.test.named", primary = "default")
    @Refreshable
    NamedInterface namedInterface(@Parameter String name) {
        return () -> name;
    }
}
