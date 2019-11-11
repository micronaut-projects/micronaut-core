package io.micronaut.docs.ioc.beans

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanProperty
import io.micronaut.core.beans.BeanWrapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IntrospectionSpec {

    @Test
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

    @Test
    fun testBeanWrapper() {
        // tag::wrapper[]
        val wrapper = BeanWrapper.getWrapper(Person("Fred")) // <1>

        wrapper.setProperty("age", "20") // <2>
        val newAge = wrapper.getRequiredProperty("age", Int::class.java) // <3>

        println("Person's age now $newAge")
        // end::wrapper[]
        assertEquals(20, newAge)
    }

    @Test
    fun testNullable() {
        val introspection = BeanIntrospection.getIntrospection(Manufacturer::class.java)
        val manufacturer: Manufacturer = introspection.instantiate(null, "John")

        val property : BeanProperty<Manufacturer, String> = introspection.getRequiredProperty("name", String::class.java)
        property.set(manufacturer, "Jane")
        val name = property.get(manufacturer)

        assertEquals("Jane", name)
    }

    @Test
    fun testVehicle() {
        val introspection = BeanIntrospection.getIntrospection(Vehicle::class.java)
        val vehicle = introspection.instantiate("Subaru", "WRX", 2)
        assertEquals("Subaru", vehicle.make)
        assertEquals("WRX", vehicle.model)
        assertEquals(2, vehicle.axels)
    }

    @Test
    fun testBusiness() {
        val introspection = BeanIntrospection.getIntrospection(Business::class.java)
        val business = introspection.instantiate("Apple")
        assertEquals("Apple", business.name)
    }
}
