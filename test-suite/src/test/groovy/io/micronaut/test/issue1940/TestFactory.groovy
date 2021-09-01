package io.micronaut.test.issue1940

import io.micronaut.context.annotation.Factory

import jakarta.inject.Named
import jakarta.inject.Singleton
import java.util.function.Supplier

@Factory
class TestFactory {
    @Singleton
    @Named("my-str-supplier")
    Supplier<String> myStrSupplier() {
        return () -> "foo";
    }
}
