package io.micronaut.docs.inject.qualifiers.named;

import javax.inject.Singleton;

// tag::class[]
@Singleton
public class V8Engine implements Engine {
    public String start() {
        return "Starting V8";
    }

    public int getCylinders() {
        return 8;
    }

}
// end::class[]
