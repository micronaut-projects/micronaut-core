package io.micronaut.docs.ioc.validation.pojo

// tag::imports[]
import io.micronaut.docs.ioc.validation.Person

import javax.inject.Singleton
import javax.validation.Valid

// end::imports[]

// tag::class[]
@Singleton
open class PersonService {
    open fun sayHello(@Valid person: Person) {
        println("Hello ${person.name}")
    }
}
// end::class[]
