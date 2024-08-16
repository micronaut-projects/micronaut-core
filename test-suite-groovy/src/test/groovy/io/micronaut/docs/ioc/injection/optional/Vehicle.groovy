package io.micronaut.docs.ioc.injection.optional

// test disabled optional injection not supported
// see https://github.com/micronaut-projects/micronaut-core/pull/10830 for discussion
//import Autowired
import jakarta.inject.Singleton

@Singleton
class Vehicle {
//    @Autowired(required = false) // <1>
    Engine engine = new Engine()

    void start() {
        engine.start()
    }
}

@Singleton
class Engine {
    void start() {
        println("Vrooom!")
    }
}
