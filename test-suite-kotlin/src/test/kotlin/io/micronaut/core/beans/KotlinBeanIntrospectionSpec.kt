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
}
