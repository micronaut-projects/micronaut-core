package io.micronaut.docs.qualifiers.annotation;

import javax.inject.Singleton;

// tag::class[]
@Singleton
public class V6Engine implements Engine { // <2>
    private int cylinders = 6;

    public int getCylinders() {
        return cylinders;
    }

    public String start() {
        return "Starting V6";
    }
}
// end::class[]
