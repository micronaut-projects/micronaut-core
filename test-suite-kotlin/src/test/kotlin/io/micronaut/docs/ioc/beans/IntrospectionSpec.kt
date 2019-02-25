package io.micronaut.docs.ioc.beans

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanProperty
import junit.framework.TestCase

class IntrospectionSpec : TestCase() {
    fun testRetrieveInspection() {

        // tag::usage[]
        val introspection = BeanIntrospection.getIntrospection(Person::class.java) // <1>
        val person : Person = introspection.instantiate("John") // <2>
        print("Hello ${person.name}")

        val property : BeanProperty<Person, String> = introspection.getRequiredProperty("name", String::class.java) // <3>
        property.set(person, "Fred") // <4>
        val name = property.get(person) // <5>
        print("Hello ${person.name}")
        // end::usage[]

        assertEquals("Fred", name)
    }
}