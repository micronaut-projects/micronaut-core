package io.micronaut.docs.ioc.injection.optional

// test disabled optional injection not supported
// see https://github.com/micronaut-projects/micronaut-core/pull/10830 for discussion
//import io.micronaut.inject.autowired.Autowired
import jakarta.inject.Singleton

@Singleton
class Vehicle {
//    @io.micronaut.inject.autowired.Autowired(required = false)
    var engine:  Engine = Engine() // <1>

    fun start() {
        engine.start()
    }
}

@Singleton
class Engine {
    fun start() {
        println("Vrooom!")
    }
}
