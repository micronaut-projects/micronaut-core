package io.micronaut.docs.ioc.beans

// tag::imports[]
import groovy.transform.Canonical
import io.micronaut.core.annotation.Introspected
// end::imports[]

// tag::class[]
@Introspected
@Canonical
class Person {

    String name

    Person(String name) {
        this.name = name
    }
}
// end::class[]
