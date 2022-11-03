package io.micronaut.inject.context.register;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
class AbcFactory {

    @Singleton
    Abc produce() {
        return new Abc();
    }
}
