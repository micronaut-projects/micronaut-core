package io.micronaut.docs.ioc.validation

// tag::imports[]
import javax.inject.Singleton
import javax.validation.constraints.NotBlank

// end::imports[]

// tag::class[]
@Singleton
open class PersonService {
    open fun sayHello(@NotBlank name: String) {
        println("Hello $name")
    }
}
// end::class[]
