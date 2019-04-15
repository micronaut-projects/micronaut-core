package io.micronaut.docs.ioc.validation

// tag::imports[]
import javax.inject.Singleton
import javax.validation.constraints.NotBlank
// end::imports[]

// tag::class[]
@Singleton
class PersonService {

    void sayHello(@NotBlank String name) {
        println "Hello $name"
    }
}
// end::class[]
