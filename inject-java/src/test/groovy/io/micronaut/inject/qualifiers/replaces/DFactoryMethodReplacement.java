package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;

import javax.inject.Singleton;

@Factory
public class DFactoryMethodReplacement {

    @Singleton
    @Replaces(value = D.class, factory = DFactory.class)
    D getD() {
        return new D2();
    }
}
