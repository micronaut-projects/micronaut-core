package io.micronaut.docs.config.value

import io.micronaut.context.annotation.Value

// tag::imports[]
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class EngineImpl implements Engine {

    @Value('${my.engine.cylinders:6}') // <1>
    protected int cylinders

    @Override
    int getCylinders() {
        this.cylinders
    }

    String start() { // <2>
        "Starting V${cylinders} Engine"
    }
}
// end::class[]