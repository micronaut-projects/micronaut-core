package io.micronaut.docs.inject.qualifiers.named;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

// tag::class[]
@Singleton
public class Vehicle {
    private final Engine engine;

    @Inject
    public Vehicle(@Named("v8") Engine engine) {// <4>
        this.engine = engine;
    }

    public String start() {
        return engine.start();// <5>
    }
}
// end::class[]