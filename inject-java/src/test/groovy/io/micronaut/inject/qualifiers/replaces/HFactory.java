package io.micronaut.inject.qualifiers.replaces;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;

import javax.inject.Singleton;

@Factory
public class HFactory {

    @Singleton
    @Replaces(HImpl.class)
    H myInterface() {
        return name -> "replacement " + name;
    }
}
