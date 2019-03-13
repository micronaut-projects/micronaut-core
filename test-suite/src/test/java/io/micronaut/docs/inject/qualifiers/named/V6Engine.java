package io.micronaut.docs.inject.qualifiers.named;

import javax.inject.Singleton;

// tag::class[]
@Singleton
public class V6Engine implements Engine {  // <2>
    public String start() {
        return "Starting V6";
    }

    public int getCylinders() {
        return 6;
    }
}
// end::class[]
