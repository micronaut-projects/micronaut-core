package io.micronaut.docs.qualifiers.annotationmember;

import javax.inject.Inject;
import javax.inject.Singleton;

// tag::class[]
@Singleton
public class Vehicle {
    final Engine engine;

    // tag::constructor[]
    @Inject Vehicle(@Cylinders(8) Engine engine) {
        this.engine = engine;
    }
    // end::constructor[]

    String start() {
        return engine.start();
    }
}
// end::class[]