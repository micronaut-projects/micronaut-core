package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.context.annotation.Factory;

import javax.inject.Singleton;

@Factory
public class CFactory {

    @Singleton
    C getC() {
        return new C1();
    }
}
