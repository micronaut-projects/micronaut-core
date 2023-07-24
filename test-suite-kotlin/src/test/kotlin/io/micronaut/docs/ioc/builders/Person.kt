package io.micronaut.docs.ioc.builders

import io.micronaut.core.annotation.Introspected

// tag::class[]
@Introspected(builder = Introspected.IntrospectionBuilder(builderClass = Person.Builder::class))
data class Person private constructor(val name: String, val age: Int) {
    data class Builder(
        var name: String? = null,
        var age: Int = 0
    ) {
        fun name(name: String) = apply { this.name = name }
        fun age(age: Int) = apply { this.age = age }

        fun build(): Person {
            requireNotNull(name) { "Name must be specified" }
            require(age >= 1) { "Age must be a positive number" }
            return Person(name!!, age)
        }
    }
}
//end::class[]
