package io.micronaut.docs.ioc.builders

import io.micronaut.core.annotation.Introspected

// tag::class[]
@Introspected(builder = Introspected.IntrospectionBuilder(builderClass = Person.Builder::class))
data class Person(val name: String, val age: Int) {
    class Builder {
        private var name: String? = null
        private var age = 0
        fun name(name: String): Builder {
            this.name = name
            return this
        }

        fun age(age: Int): Builder {
            this.age = age
            return this
        }

        fun build(): Person {
            requireNotNull(name) { "Name must be specified" }
            require(age >= 1) { "Age must be a positive number" }
            return Person(name!!, age)
        }
    }
}
