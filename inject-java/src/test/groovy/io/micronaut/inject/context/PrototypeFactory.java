package io.micronaut.inject.context;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;

@Factory
public class PrototypeFactory {

    @Prototype
    Pojo prototype() {
        return new Pojo();
    }
}
