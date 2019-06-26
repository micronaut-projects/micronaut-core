package io.micronaut.docs.ioc.beans

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanProperty
import io.micronaut.core.beans.BeanWrapper

class IntrospectionSpec : StringSpec ({

    "test retrieve inspection" {

        // tag::usage[]
        val introspection = BeanIntrospection.getIntrospection(Person::class.java) // <1>
        val person : Person = introspection.instantiate("John") // <2>
        print("Hello ${person.name}")

        val property : BeanProperty<Person, String> = introspection.getRequiredProperty("name", String::class.java) // <3>
        property.set(person, "Fred") // <4>
        val name = property.get(person) // <5>
        print("Hello ${person.name}")
        // end::usage[]

        name.shouldBe("Fred")
    }

    "test bean wrapper" {
        // tag::wrapper[]
        val wrapper = BeanWrapper.getWrapper(Person("Fred")) // <1>

        wrapper.setProperty("age", "20") // <2>
        val newAge = wrapper.getRequiredProperty("age", Int::class.java) // <3>

        println("Person's age now $newAge")
        // end::wrapper[]
        newAge.shouldBe(20)
    }

    "test nullable" {
        val introspection = BeanIntrospection.getIntrospection(Manufacturer::class.java)
        val manufacturer: Manufacturer = introspection.instantiate(null, "John")

        val property : BeanProperty<Manufacturer, String> = introspection.getRequiredProperty("name", String::class.java)
        property.set(manufacturer, "Jane")
        val name = property.get(manufacturer)

        name.shouldBe("Jane")
    }
})
