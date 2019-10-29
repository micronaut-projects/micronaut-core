package io.micronaut.docs.lifecycle

// tag::imports[]
import javax.annotation.PostConstruct // <1>
import javax.inject.Singleton
// end::imports[]

/**
 * @author Graeme Rocher
 * @since 1.0
 */
// tag::class[]
@Singleton
class V8Engine implements Engine {
    int cylinders = 8
    boolean initialized = false // <2>

    String start() {
        if(!initialized) throw new IllegalStateException("Engine not initialized!")

        return "Starting V8"
    }

    @PostConstruct // <3>
    void initialize() {
        this.initialized = true
    }
}
// end::class[]