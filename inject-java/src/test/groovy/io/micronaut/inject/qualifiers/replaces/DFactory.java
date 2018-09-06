package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.context.annotation.Factory;

import javax.inject.Singleton;

@Factory
public class DFactory {

    @Singleton
    D getD() {
        return new D1();
    }

}
