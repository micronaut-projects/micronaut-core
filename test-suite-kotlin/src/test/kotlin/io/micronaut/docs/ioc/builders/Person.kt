package io.micronaut.docs.ioc.builders

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.ReflectiveAccess

// tag::class[]
@ReflectiveAccess
@Introspected(builder = Introspected.IntrospectionBuilder(
    builderClass = Person.Builder::class
))
class Person private constructor(val name: String, val age: Int) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Person) return false
        return age == other.age && name == other.name
    }

    override fun hashCode(): Int = 31 * name.hashCode() + age

    class Builder {
        private var name: String? = null
        private var age: Int = 0

        fun name(name: String): Builder {
            this.name = name
            return this
        }

        fun age(age: Int): Builder {
            this.age = age
            return this
        }

        fun build(): Person {
            val name = requireNotNull(name)
            require(age >= 1) { "Age must be a positive number" }
            return Person(name, age)
        }
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}
// end::class[]
