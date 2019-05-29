package io.micronaut.docs.server.intro;

// tag::imports[]
import io.micronaut.runtime.Micronaut;
// end::imports[]

// tag::class[]
public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class);
    }
}
// end::class[]
