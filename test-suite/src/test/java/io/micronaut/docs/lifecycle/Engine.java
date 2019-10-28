package io.micronaut.docs.lifecycle;

// tag::class[]
public interface Engine { // <1>
    int getCylinders();
    String start();
}
// end::class[]