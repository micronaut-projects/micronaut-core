package io.micronaut.test.issue1940

import io.micronaut.context.annotation.Factory

import javax.inject.Named
import javax.inject.Singleton
import java.util.function.Supplier

@Factory
class TestFactory {
    @Singleton
    @Named("my-str-supplier")
    Supplier<String> myStrSupplier() {
        return () -> "foo";
    }
}
