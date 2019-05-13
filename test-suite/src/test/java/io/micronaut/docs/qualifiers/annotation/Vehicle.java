package io.micronaut.docs.qualifiers.annotation;

import javax.inject.Inject;
import javax.inject.Singleton;

// tag::class[]
@Singleton
public class Vehicle {
    final Engine engine;

    // tag::constructor[]
    @Inject Vehicle(@V8 Engine engine) {
        this.engine = engine;
    }
    // end::constructor[]

    String start() {
        return engine.start(); // <5>
    }
}
// end::class[]