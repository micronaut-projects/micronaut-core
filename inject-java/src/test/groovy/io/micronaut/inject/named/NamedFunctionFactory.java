package io.micronaut.inject.named;

import io.micronaut.context.annotation.Factory;

import javax.inject.Named;
import javax.inject.Singleton;

@Factory
public class NamedFunctionFactory {

    @Named("INPUT")
    @Singleton
    NamedFunction inputFunction() {
        return s -> "INPUT " + s;
    }

    @Named("OUTPUT")
    @Singleton
    NamedFunction outputFunction() {
        return s -> "OUTPUT " + s;
    }
}
