package io.micronaut.docs.lifecycle

// tag::imports[]
import javax.annotation.PostConstruct
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class V8Engine : Engine {
    override var cylinders = 8
    var initialized = false // <2>

    override fun start(): String {
        if (!initialized) throw IllegalStateException("Engine not initialized!")

        return "Starting V8"
    }

    @PostConstruct // <3>
    fun initialize() {
        this.initialized = true
    }
}
// end::class[]