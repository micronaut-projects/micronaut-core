package io.micronaut.docs.qualifiers.annotation;

import javax.inject.Singleton;

// tag::class[]
@Singleton
public class V8Engine implements Engine { // <2>
    private int cylinders = 8;

    public int getCylinders() {
        return cylinders;
    }

    public String start() {
        return "Starting V8";
    }
}
// end::class[]
