package io.micronaut.core.beans

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KotlinBeanIntrospectionSpec {

    @Test
    fun testWithValueOnKotlinDataClassWithDefaultValues() {
        val introspection = BeanIntrospection.getIntrospection(SomeEntity::class.java)

        val instance = introspection.instantiate(10L, "foo")

        assertEquals(10, instance.id)
        assertEquals("foo", instance.something)

        val changed = introspection.getRequiredProperty("something", String::class.java)
            .withValue(instance, "changed")

        assertEquals(10, changed.id)
        assertEquals("changed", changed.something)

    }

    @Test
    fun testIsProperties() {
        val introspection = BeanIntrospection.getIntrospection(TestEntity::class.java)

        assertEquals(listOf("id", "name", "getSurname", "isDeleted", "isImportant", "corrected", "upgraded", "isMyBool", "isMyBool2", "myBool3", "myBool4", "myBool5"), introspection.propertyNames.asList())

        val introspection2 = BeanIntrospection.getIntrospection(TestEntity2::class.java)

        assertEquals(listOf("id", "name", "getSurname", "isDeleted", "isImportant", "corrected", "upgraded", "isMyBool", "isMyBool2", "myBool3", "myBool4", "myBool5"), introspection2.propertyNames.asList())
    }

    @Test
    fun testDefaults() {
        val introspection = BeanIntrospection.getIntrospection(TestEntity3::class.java)

        val instance1 = introspection.instantiate(null, "Stepanov", null, 123)

        assertEquals("Denis", instance1.firstName)
        assertEquals("Stepanov", instance1.lastName)
        assertEquals("IT", instance1.job)
        assertEquals(123, instance1.age)

        val instance2 = introspection.instantiate("Jeff", "Hello", null, 123)

        assertEquals("Jeff", instance2.firstName)
        assertEquals("Hello", instance2.lastName)
        assertEquals("IT", instance2.job)
        assertEquals(123, instance2.age)

        val instance3 = introspection.instantiate(null, "Hello", "HR", 22)

        assertEquals("Denis", instance3.firstName)
        assertEquals("Hello", instance3.lastName)
        assertEquals("HR", instance3.job)
        assertEquals(22, instance3.age)
    }
}
