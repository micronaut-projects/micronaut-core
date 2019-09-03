package io.micronaut.docs.config.property;

// tag::imports[]
import io.micronaut.context.annotation.Property

import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class Engine {

    @Property(name = "my.engine.cylinders") // <1>
    protected int cylinders // <2>

    @Property(name = "my.engine.manufacturer") //<3>
    String manufacturer

    int getCylinders() {
        cylinders
    }
}
// end::class[]
